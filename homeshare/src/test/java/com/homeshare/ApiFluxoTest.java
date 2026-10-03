package com.homeshare;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste "de ponta a ponta": sobe a aplicação inteira (segurança, JWT, controllers, banco)
 * e chama os endpoints como um cliente HTTP faria, conferindo os códigos 400/401/403/404/409.
 * Se este teste passa, o contexto do Spring está configurado corretamente.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiFluxoTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    // ---------- auxiliares ----------

    private String emailUnico(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
    }

    private void registrar(String nome, String email) throws Exception {
        mvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"%s","email":"%s","senha":"senha12345"}
                                """.formatted(nome, email)))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        String corpo = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"senha12345"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("token").asText();
    }

    /** Registra e já faz login; devolve o token. */
    private String novoUsuarioComToken(String nome) throws Exception {
        String email = emailUnico(nome.toLowerCase());
        registrar(nome, email);
        return login(email);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    // ---------- fluxo principal ----------

    @Test
    void fluxoCompleto_com_sucessao_de_lideranca() throws Exception {
        String tokenAna = novoUsuarioComToken("Ana");
        String tokenBruno = novoUsuarioComToken("Bruno");
        String tokenCarla = novoUsuarioComToken("Carla");

        // Ana cria a casa e vira LIDER
        String corpoCasa = mvc.perform(post("/casas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenAna))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"República\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("LIDER"))
                .andExpect(jsonPath("$.codigoConvite").exists())
                .andReturn().getResponse().getContentAsString();
        long casaId = json.readTree(corpoCasa).get("id").asLong();
        String codigo = json.readTree(corpoCasa).get("codigoConvite").asText();
        String corpoEntrar = "{\"codigo\":\"" + codigo + "\"}";

        // Bruno e Carla entram (nessa ordem) como MORADOR
        mvc.perform(post("/casas/entrar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenBruno))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEntrar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("MORADOR"))
                .andExpect(jsonPath("$.codigoConvite").doesNotExist());
        mvc.perform(post("/casas/entrar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCarla))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEntrar))
                .andExpect(status().isOk());

        // 409: Bruno não pode entrar duas vezes
        mvc.perform(post("/casas/entrar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenBruno))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEntrar))
                .andExpect(status().isConflict());

        // Lista com 3 membros
        mvc.perform(get("/casas/" + casaId + "/membros")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenBruno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        // Ana (líder) sai: Bruno, o morador mais antigo, vira líder
        mvc.perform(post("/casas/" + casaId + "/sair")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenAna)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.novoLider.nome").value("Bruno"))
                .andExpect(jsonPath("$.novoLider.papel").value("LIDER"));

        // 403: Ana saiu e não vê mais a casa
        mvc.perform(get("/casas/" + casaId + "/membros")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenAna)))
                .andExpect(status().isForbidden());

        // Bruno vê 2 membros, e ele é o líder
        mvc.perform(get("/casas/" + casaId + "/membros")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenBruno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Bruno"))
                .andExpect(jsonPath("$[0].papel").value("LIDER"))
                .andExpect(jsonPath("$[1].nome").value("Carla"))
                .andExpect(jsonPath("$[1].papel").value("MORADOR"));
    }

    // ---------- 401 ----------

    @Test
    void rota_protegida_sem_token_retorna_401() throws Exception {
        mvc.perform(get("/casas/1/membros"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void token_invalido_retorna_401() throws Exception {
        mvc.perform(get("/casas/1/membros")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer isto.nao.e.um.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_com_senha_errada_retorna_401() throws Exception {
        String email = emailUnico("dani");
        registrar("Dani", email);

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senhaErrada1\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 400 ----------

    @Test
    void registro_com_dados_invalidos_retorna_400() throws Exception {
        mvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"\",\"email\":\"nao-e-email\",\"senha\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.email").exists())
                .andExpect(jsonPath("$.campos.senha").exists());
    }

    @Test
    void criar_casa_sem_nome_retorna_400() throws Exception {
        String token = novoUsuarioComToken("Edu");

        mvc.perform(post("/casas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void id_da_casa_nao_numerico_retorna_400() throws Exception {
        String token = novoUsuarioComToken("Fabi");

        mvc.perform(get("/casas/abc/membros")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    // ---------- 409 e 404 ----------

    @Test
    void registro_com_email_repetido_retorna_409() throws Exception {
        String email = emailUnico("gabi");
        registrar("Gabi", email);

        mvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Outra Gabi\",\"email\":\"" + email + "\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void entrar_com_codigo_inexistente_retorna_404() throws Exception {
        String token = novoUsuarioComToken("Hugo");

        mvc.perform(post("/casas/entrar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"NAOEXISTE1\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listar_membros_de_casa_inexistente_retorna_404() throws Exception {
        String token = novoUsuarioComToken("Iara");

        mvc.perform(get("/casas/999999/membros")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    // ---------- 403 ----------

    @Test
    void nao_membro_listando_membros_retorna_403() throws Exception {
        String tokenDono = novoUsuarioComToken("Joao");
        String tokenIntruso = novoUsuarioComToken("Kleber");

        String corpo = mvc.perform(post("/casas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Casa do João\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long casaId = json.readTree(corpo).get("id").asLong();

        mvc.perform(get("/casas/" + casaId + "/membros")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenIntruso)))
                .andExpect(status().isForbidden());
    }
}
