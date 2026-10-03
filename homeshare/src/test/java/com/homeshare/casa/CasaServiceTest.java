package com.homeshare.casa;

import com.homeshare.casa.dto.CasaResponse;
import com.homeshare.casa.dto.CriarCasaRequest;
import com.homeshare.casa.dto.EntrarCasaRequest;
import com.homeshare.casa.dto.MembroResponse;
import com.homeshare.casa.dto.SaidaResponse;
import com.homeshare.exception.AcessoNegadoException;
import com.homeshare.exception.ConflitoException;
import com.homeshare.exception.RecursoNaoEncontradoException;
import com.homeshare.usuario.Usuario;
import com.homeshare.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa as regras do CasaService com um banco H2 de verdade (não com mocks).
 * @DataJpaTest sobe só a camada de persistência e desfaz (rollback) cada teste ao final.
 */
@DataJpaTest
@Import(CasaService.class)
class CasaServiceTest {

    @Autowired
    private CasaService casaService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private MembroRepository membroRepository;
    @Autowired
    private CasaRepository casaRepository;

    // ---------- auxiliares ----------

    private Usuario novoUsuario(String nome) {
        return usuarioRepository.save(new Usuario(nome, nome.toLowerCase() + "@teste.com", "hash"));
    }

    private CasaResponse criarCasa(Usuario dono) {
        return casaService.criarCasa(dono.getId(), new CriarCasaRequest("República"));
    }

    private void entrar(Usuario usuario, CasaResponse casa) {
        casaService.entrar(usuario.getId(), new EntrarCasaRequest(casa.codigoConvite()));
    }

    private Membro vinculo(Usuario usuario, CasaResponse casa) {
        return membroRepository.findByUsuarioIdAndCasaId(usuario.getId(), casa.id()).orElseThrow();
    }

    // ---------- criar e entrar ----------

    @Test
    void criador_da_casa_vira_lider() {
        Usuario ana = novoUsuario("Ana");

        CasaResponse casa = criarCasa(ana);

        assertThat(casa.papel()).isEqualTo(PapelMembro.LIDER);
        assertThat(casa.codigoConvite()).isNotBlank();
        assertThat(vinculo(ana, casa).getPapel()).isEqualTo(PapelMembro.LIDER);
    }

    @Test
    void quem_entra_por_codigo_vira_morador() {
        Usuario ana = novoUsuario("Ana");
        Usuario bruno = novoUsuario("Bruno");
        CasaResponse casa = criarCasa(ana);

        CasaResponse resposta = casaService.entrar(bruno.getId(), new EntrarCasaRequest(casa.codigoConvite()));

        assertThat(resposta.papel()).isEqualTo(PapelMembro.MORADOR);
        assertThat(resposta.codigoConvite()).isNull(); // o código só é devolvido a quem cria a casa
    }

    @Test
    void codigo_aceita_minusculas_e_espacos() {
        Usuario ana = novoUsuario("Ana");
        Usuario bruno = novoUsuario("Bruno");
        CasaResponse casa = criarCasa(ana);

        CasaResponse resposta = casaService.entrar(bruno.getId(),
                new EntrarCasaRequest("  " + casa.codigoConvite().toLowerCase() + " "));

        assertThat(resposta.id()).isEqualTo(casa.id());
    }

    @Test
    void codigo_inexistente_gera_nao_encontrado() {
        Usuario ana = novoUsuario("Ana");

        assertThatThrownBy(() -> casaService.entrar(ana.getId(), new EntrarCasaRequest("NAOEXISTE1")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void nao_pode_entrar_duas_vezes_na_mesma_casa() {
        Usuario ana = novoUsuario("Ana");
        Usuario bruno = novoUsuario("Bruno");
        CasaResponse casa = criarCasa(ana);
        entrar(bruno, casa);

        assertThatThrownBy(() -> entrar(bruno, casa)).isInstanceOf(ConflitoException.class);
    }

    @Test
    void lider_tambem_nao_pode_entrar_na_propria_casa() {
        Usuario ana = novoUsuario("Ana");
        CasaResponse casa = criarCasa(ana);

        assertThatThrownBy(() -> entrar(ana, casa)).isInstanceOf(ConflitoException.class);
    }

    // ---------- ver a casa ----------

    @Test
    void nao_membro_nao_ve_os_membros_da_casa() {
        Usuario ana = novoUsuario("Ana");
        Usuario intruso = novoUsuario("Intruso");
        CasaResponse casa = criarCasa(ana);

        assertThatThrownBy(() -> casaService.listarMembros(intruso.getId(), casa.id()))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void casa_inexistente_gera_nao_encontrado() {
        Usuario ana = novoUsuario("Ana");

        assertThatThrownBy(() -> casaService.listarMembros(ana.getId(), 999_999L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void lista_membros_ativos_do_mais_antigo_para_o_mais_novo() {
        Usuario ana = novoUsuario("Ana");
        Usuario bruno = novoUsuario("Bruno");
        Usuario carla = novoUsuario("Carla");
        CasaResponse casa = criarCasa(ana);
        entrar(bruno, casa);
        entrar(carla, casa);

        List<MembroResponse> membros = casaService.listarMembros(bruno.getId(), casa.id());

        assertThat(membros).extracting(MembroResponse::nome).containsExactly("Ana", "Bruno", "Carla");
        assertThat(membros).extracting(MembroResponse::papel)
                .containsExactly(PapelMembro.LIDER, PapelMembro.MORADOR, PapelMembro.MORADOR);
    }

    // ---------- sair e sucessão ----------

    @Test
    void lider_sai_e_o_morador_ativo_mais_antigo_assume() {
        Usuario ana = novoUsuario("Ana");
        Usuario bruno = novoUsuario("Bruno"); // entrou primeiro
        Usuario carla = novoUsuario("Carla"); // entrou depois
        CasaResponse casa = criarCasa(ana);
        entrar(bruno, casa);
        entrar(carla, casa);

        SaidaResponse saida = casaService.sair(ana.getId(), casa.id());

        assertThat(saida.novoLider()).isNotNull();
        assertThat(saida.novoLider().usuarioId()).isEqualTo(bruno.getId());
        assertThat(vinculo(bruno, casa).getPapel()).isEqualTo(PapelMembro.LIDER);
        assertThat(vinculo(carla, casa).getPapel()).isEqualTo(PapelMembro.MORADOR);
    }

    @Test
    void depois_da_sucessao_a_casa_tem_exatamente_um_lider() {
        Usuario ana = novoUsuario("Ana");
        Usuario bruno = novoUsuario("Bruno");
        Usuario carla = novoUsuario("Carla");
        CasaResponse casa = criarCasa(ana);
        entrar(bruno, casa);
        entrar(carla, casa);

        casaService.sair(ana.getId(), casa.id());

        List<MembroResponse> membros = casaService.listarMembros(carla.getId(), casa.id());
        assertThat(membros).filteredOn(m -> m.papel() == PapelMembro.LIDER).hasSize(1);
        assertThat(membros).hasSize(2); // Ana (inativa) não aparece mais
    }

    @Test
    void quem_sai_fica_inativo_e_o_historico_e_preservado() {
        Usuario ana = novoUsuario("Ana");
        Usuario bruno = novoUsuario("Bruno");
        CasaResponse casa = criarCasa(ana);
        entrar(bruno, casa);

        casaService.sair(ana.getId(), casa.id());

        Membro registroDaAna = vinculo(ana, casa); // a linha continua no banco
        assertThat(registroDaAna.isAtivo()).isFalse();
        assertThat(registroDaAna.getDataSaida()).isNotNull();
        assertThat(registroDaAna.getDataEntrada()).isNotNull();
    }

    @Test
    void morador_comum_sai_e_o_lider_continua_o_mesmo() {
        Usuario ana = novoUsuario("Ana");
        Usuario bruno = novoUsuario("Bruno");
        CasaResponse casa = criarCasa(ana);
        entrar(bruno, casa);

        SaidaResponse saida = casaService.sair(bruno.getId(), casa.id());

        assertThat(saida.novoLider()).isNull();
        assertThat(vinculo(ana, casa).getPapel()).isEqualTo(PapelMembro.LIDER);
        assertThat(vinculo(bruno, casa).isAtivo()).isFalse();
    }

    @Test
    void quem_saiu_nao_ve_mais_a_casa() {
        Usuario ana = novoUsuario("Ana");
        Usuario bruno = novoUsuario("Bruno");
        CasaResponse casa = criarCasa(ana);
        entrar(bruno, casa);
        casaService.sair(bruno.getId(), casa.id());

        assertThatThrownBy(() -> casaService.listarMembros(bruno.getId(), casa.id()))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void quem_ja_saiu_nao_pode_sair_de_novo() {
        Usuario ana = novoUsuario("Ana");
        Usuario bruno = novoUsuario("Bruno");
        CasaResponse casa = criarCasa(ana);
        entrar(bruno, casa);
        casaService.sair(bruno.getId(), casa.id());

        assertThatThrownBy(() -> casaService.sair(bruno.getId(), casa.id()))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void quem_saiu_pode_voltar_como_morador_sem_duplicar_o_vinculo() {
        Usuario ana = novoUsuario("Ana");
        Usuario bruno = novoUsuario("Bruno");
        CasaResponse casa = criarCasa(ana);
        entrar(bruno, casa);
        casaService.sair(bruno.getId(), casa.id());

        entrar(bruno, casa);

        Membro registro = vinculo(bruno, casa);
        assertThat(registro.isAtivo()).isTrue();
        assertThat(registro.getPapel()).isEqualTo(PapelMembro.MORADOR);
        assertThat(registro.getDataSaida()).isNull();
        assertThat(casaService.listarMembros(ana.getId(), casa.id())).hasSize(2);
    }

    @Test
    void ultimo_morador_sai_e_a_casa_e_encerrada() {
        Usuario ana = novoUsuario("Ana");
        Usuario bruno = novoUsuario("Bruno");
        CasaResponse casa = criarCasa(ana);

        SaidaResponse saida = casaService.sair(ana.getId(), casa.id());

        assertThat(saida.novoLider()).isNull();
        assertThat(casaRepository.findById(casa.id()).orElseThrow().isAtiva()).isFalse();
        assertThatThrownBy(() -> entrar(bruno, casa)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
