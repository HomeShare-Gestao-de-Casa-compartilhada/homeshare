# HomeShare — back-end (entrega 1: contas, casas e membros)

API REST em Java 21 + Spring Boot 3.3.5. Escopo desta entrega:
cadastro/login (JWT + BCrypt), criar casa, entrar por código, listar membros e sair
(com sucessão de liderança). Tarefas, reservas, avisos e gastos **não** fazem parte desta entrega.

## Como rodar

Pré-requisitos: JDK 21 e Maven 3.9+ (ou abra a pasta no IntelliJ, que traz o Maven embutido).

```bash
mvn test                # roda os testes (confirma que tudo compila e as regras funcionam)
mvn spring-boot:run     # sobe a API em http://localhost:8080
```

O banco H2 é em memória: **os dados somem quando a aplicação para**. Ao subir, o console mostra
uma linha "Using generated security password" — é um aviso padrão do Spring Boot que pode ser
ignorado (o login usado aqui é o nosso, via JWT).

## Endpoints

| Método | Rota | Auth | Sucesso | Erros possíveis |
|---|---|---|---|---|
| POST | `/auth/registro` | não | 201 | 400, 409 (e-mail repetido) |
| POST | `/auth/login` | não | 200 + token | 400, 401 |
| POST | `/casas` | sim | 201 (você vira LIDER) | 400, 401 |
| POST | `/casas/entrar` | sim | 200 (você vira MORADOR) | 400, 401, 404 (código), 409 (já é membro) |
| GET | `/casas/{id}/membros` | sim | 200 | 401, 403 (não é membro), 404 |
| POST | `/casas/{id}/sair` | sim | 200 | 401, 403, 404 |

Significado dos códigos: **400** dados inválidos · **401** sem token / token inválido / login errado ·
**403** autenticado mas não mora na casa · **404** casa ou código inexistente · **409** conflito (já existe / já é membro).

Todo erro tem o mesmo formato: `{ "timestamp", "status", "erro", "mensagem", "campos"? }`.

## Roteiro de demonstração (curl)

```bash
# 1. cadastrar e logar (repita para Bruno)
curl -s -X POST localhost:8080/auth/registro -H "Content-Type: application/json" \
  -d '{"nome":"Ana","email":"ana@teste.com","senha":"senha12345"}'
curl -s -X POST localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"email":"ana@teste.com","senha":"senha12345"}'          # copie o "token"

# 2. Ana cria a casa (a resposta traz o codigoConvite)
curl -s -X POST localhost:8080/casas -H "Authorization: Bearer TOKEN_ANA" \
  -H "Content-Type: application/json" -d '{"nome":"República"}'

# 3. Bruno entra com o código
curl -s -X POST localhost:8080/casas/entrar -H "Authorization: Bearer TOKEN_BRUNO" \
  -H "Content-Type: application/json" -d '{"codigo":"CODIGO"}'

# 4. listar membros e sair (a líder sai: Bruno assume)
curl -s localhost:8080/casas/1/membros -H "Authorization: Bearer TOKEN_BRUNO"
curl -s -X POST localhost:8080/casas/1/sair -H "Authorization: Bearer TOKEN_ANA"
```

## Estrutura (pacote por funcionalidade)

```
com.homeshare
├── auth/        AuthController -> AuthService (+ JwtService, JwtAuthFilter) ; dto/
├── usuario/     Usuario (entity), UsuarioRepository, UsuarioResponse
├── casa/        CasaController -> CasaService -> CasaRepository / MembroRepository
│                Casa, Membro, PapelMembro (entities/enum) ; dto/
├── config/      SecurityConfig, RestAuthenticationEntryPoint
└── exception/   exceções de negócio + GlobalExceptionHandler + ErroResponse
```

Fluxo de uma requisição: **Filtro JWT** (identifica quem é) → **Controller** (recebe/valida o JSON)
→ **Service** (regras de negócio) → **Repository** (banco) → **Entity**. Erros viram HTTP no `GlobalExceptionHandler`.

## Modelo de dados

`Usuario` 1—N `Membro` N—1 `Casa`. **Membro** é a tabela de ligação e guarda: `papel` (LIDER/MORADOR),
`ativo`, `dataEntrada`, `dataSaida`. Restrição UNIQUE (usuario, casa): uma única linha por pessoa em cada casa.

## Comparação com Python/FastAPI

| Spring | FastAPI |
|---|---|
| `@RestController` + `@PostMapping` | `@app.post(...)` / `APIRouter` |
| `record` DTO + `@Valid` | modelo Pydantic |
| `@Service` (injeção por construtor) | funções/classes de serviço + `Depends` |
| `JpaRepository` | SQLAlchemy Session/CRUD (o Spring gera as consultas pelo nome do método) |
| `@Entity` | modelo SQLAlchemy |
| `@Transactional` | `with session.begin():` — tudo ou nada |
| dirty checking (alterar a entidade e pronto, sem `save`) | objeto da sessão alterado + `commit()` |
| `JwtAuthFilter` | dependência `get_current_user` |
| `@RestControllerAdvice` | `@app.exception_handler` |

## Decisões de projeto (possíveis perguntas do professor)

1. **Papel em `Membro`, não em `Usuario`.** A mesma pessoa pode ser líder de uma casa e morador de outra (o escopo permite várias casas).
2. **Sair = inativar, não apagar.** `ativo=false` + `dataSaida`. O histórico (futuras tarefas, gastos) continua apontando para a pessoa.
3. **Sucessão:** ao sair, o líder é substituído pelo morador **ativo mais antigo** (`dataEntrada`, desempate por id). Tudo na mesma transação: nunca existe casa sem líder nem com dois.
4. **Quem sai deixa de ser LIDER** no registro (vira MORADOR inativo), garantindo no máximo um LIDER por casa.
5. **Voltar à casa:** reativa a mesma linha como MORADOR, e a antiguidade recomeça da nova entrada (não dá para "voltar sendo o mais antigo").
6. **Último morador sai:** a casa é encerrada (`ativa=false`) e o código deixa de funcionar — assim ninguém entra numa casa sem líder.
7. **401 × 403:** 401 = não sei quem você é (sem token/inválido); 403 = sei quem é, mas não pode (não mora na casa).
8. **Senha:** só o hash BCrypt é guardado. A mensagem de login errado é a mesma para e-mail inexistente e senha errada (não revela quais e-mails existem).
9. **Código de convite** aleatório de 10 caracteres (UUID, `SecureRandom`); só é devolvido a quem cria a casa.
10. **Stateless:** sem sessão no servidor; cada requisição traz o JWT (expira em 120 min, configurável).
11. **Casa inexistente = 404; casa existente sem ser membro = 403.** Escolha consciente: dá para descobrir quais ids existem; seria possível devolver 403 nos dois casos.

## Limitações conhecidas (fora do escopo desta entrega)

- Não há "listar minhas casas", "ver detalhes da casa", gerar/cancelar convite, expulsar morador nem passar a liderança manualmente
  (o protótipo prevê todos, nas telas 2, 3, 10 e 14).
- O protótipo (tela 10) diz que o líder deve passar a liderança antes de sair; aqui, conforme o escopo pedido, o líder pode sair e a sucessão é automática.
- Banco em memória e segredo JWT padrão: apenas para desenvolvimento/demonstração.
