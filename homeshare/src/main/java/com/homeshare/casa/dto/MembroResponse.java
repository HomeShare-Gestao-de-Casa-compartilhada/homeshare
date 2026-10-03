package com.homeshare.casa.dto;

import com.homeshare.casa.Membro;
import com.homeshare.casa.PapelMembro;

import java.time.Instant;

public record MembroResponse(Long usuarioId, String nome, PapelMembro papel, Instant dataEntrada) {

    public static MembroResponse de(Membro membro) {
        return new MembroResponse(
                membro.getUsuario().getId(),
                membro.getUsuario().getNome(),
                membro.getPapel(),
                membro.getDataEntrada());
    }
}
