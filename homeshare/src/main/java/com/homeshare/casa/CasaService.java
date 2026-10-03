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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras de negócio de casas e membros.
 *
 * Cada método público é uma transação: ou tudo é gravado, ou nada. Se uma exceção
 * (RuntimeException) escapar, o Spring desfaz as alterações (rollback).
 */
@Service
public class CasaService {

    private final CasaRepository casaRepository;
    private final MembroRepository membroRepository;
    private final UsuarioRepository usuarioRepository;

    public CasaService(CasaRepository casaRepository,
                       MembroRepository membroRepository,
                       UsuarioRepository usuarioRepository) {
        this.casaRepository = casaRepository;
        this.membroRepository = membroRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /** Qualquer pessoa cria uma casa e vira LIDER dela automaticamente. */
    @Transactional
    public CasaResponse criarCasa(Long usuarioId, CriarCasaRequest requisicao) {
        Usuario usuario = buscarUsuario(usuarioId);

        Casa casa = casaRepository.save(new Casa(requisicao.nome().trim(), gerarCodigoUnico()));
        Membro lider = membroRepository.save(new Membro(usuario, casa, PapelMembro.LIDER));

        return CasaResponse.comCodigo(casa, lider.getPapel());
    }

    /** Entra numa casa pelo código de convite, como MORADOR. */
    @Transactional
    public CasaResponse entrar(Long usuarioId, EntrarCasaRequest requisicao) {
        String codigo = requisicao.codigo().trim().toUpperCase(Locale.ROOT);

        Casa casa = casaRepository.findByCodigoConvite(codigo)
                .filter(Casa::isAtiva)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Código de convite inválido."));

        Usuario usuario = buscarUsuario(usuarioId);
        Optional<Membro> vinculoExistente = membroRepository.findByUsuarioIdAndCasaId(usuarioId, casa.getId());

        Membro membro;
        if (vinculoExistente.isPresent()) {
            membro = vinculoExistente.get();
            if (membro.isAtivo()) {
                throw new ConflitoException("Você já é membro desta casa.");
            }
            membro.reativar(); // já morou aqui antes: reaproveita o histórico
        } else {
            membro = membroRepository.save(new Membro(usuario, casa, PapelMembro.MORADOR));
        }

        return CasaResponse.semCodigo(casa, membro.getPapel());
    }

    /** Lista os moradores ativos. Só quem mora na casa pode ver. */
    @Transactional(readOnly = true)
    public List<MembroResponse> listarMembros(Long usuarioId, Long casaId) {
        buscarCasa(casaId);                      // 404 se a casa não existe
        exigirMembroAtivo(usuarioId, casaId);    // 403 se não mora nela

        return membroRepository.findByCasaIdAndAtivoTrueOrderByDataEntradaAscIdAsc(casaId)
                .stream()
                .map(MembroResponse::de)
                .toList();
    }

    /**
     * Sai da casa (fica inativo, o histórico é mantido).
     * Se quem sai é o líder, o morador ativo mais antigo assume a liderança.
     */
    @Transactional
    public SaidaResponse sair(Long usuarioId, Long casaId) {
        Casa casa = buscarCasa(casaId);
        Membro saindo = exigirMembroAtivo(usuarioId, casaId);

        boolean eraLider = saindo.getPapel() == PapelMembro.LIDER;
        saindo.sair();

        if (!eraLider) {
            return new SaidaResponse("Você saiu da casa.", null);
        }

        // Sucessão: primeiro da fila de antiguidade, excluindo quem acabou de sair
        Optional<Membro> sucessor = membroRepository
                .findByCasaIdAndAtivoTrueOrderByDataEntradaAscIdAsc(casaId)
                .stream()
                .filter(m -> !m.getId().equals(saindo.getId()))
                .findFirst();

        if (sucessor.isPresent()) {
            Membro novoLider = sucessor.get();
            novoLider.promoverALider();
            return new SaidaResponse(
                    "Você saiu da casa. " + novoLider.getUsuario().getNome() + " é o novo líder.",
                    MembroResponse.de(novoLider));
        }

        casa.encerrar(); // ninguém sobrou: a casa não aceita mais convites
        return new SaidaResponse("Você saiu da casa. Como não restou nenhum morador, a casa foi encerrada.", null);
    }

    // ---------- métodos auxiliares ----------

    private Usuario buscarUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    private Casa buscarCasa(Long casaId) {
        return casaRepository.findById(casaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Casa não encontrada."));
    }

    private Membro exigirMembroAtivo(Long usuarioId, Long casaId) {
        return membroRepository.findByUsuarioIdAndCasaIdAndAtivoTrue(usuarioId, casaId)
                .orElseThrow(() -> new AcessoNegadoException("Você não é membro desta casa."));
    }

    /** 10 caracteres hexadecimais maiúsculos, ex.: "3F9A1C07BD". */
    private String gerarCodigoUnico() {
        String codigo;
        do {
            codigo = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 10).toUpperCase(Locale.ROOT);
        } while (casaRepository.existsByCodigoConvite(codigo));
        return codigo;
    }
}
