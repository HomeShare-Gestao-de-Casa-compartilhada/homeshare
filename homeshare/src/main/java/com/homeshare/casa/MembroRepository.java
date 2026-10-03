package com.homeshare.casa;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembroRepository extends JpaRepository<Membro, Long> {

    /** Vínculo do usuário com a casa, esteja ele ativo ou não. */
    Optional<Membro> findByUsuarioIdAndCasaId(Long usuarioId, Long casaId);

    /** Vínculo apenas se o usuário ainda mora na casa. */
    Optional<Membro> findByUsuarioIdAndCasaIdAndAtivoTrue(Long usuarioId, Long casaId);

    /**
     * Moradores ativos, do mais antigo para o mais novo (o id desempata).
     * O primeiro da lista é o sucessor do líder. O EntityGraph traz o usuário
     * junto na mesma consulta, evitando uma consulta extra por membro (problema N+1).
     */
    @EntityGraph(attributePaths = "usuario")
    List<Membro> findByCasaIdAndAtivoTrueOrderByDataEntradaAscIdAsc(Long casaId);
}
