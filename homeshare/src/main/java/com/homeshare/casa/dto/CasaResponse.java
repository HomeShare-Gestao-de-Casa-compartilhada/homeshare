package com.homeshare.casa.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.homeshare.casa.Casa;
import com.homeshare.casa.PapelMembro;

/** O código de convite só aparece para quem acabou de criar a casa (null é omitido do JSON). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CasaResponse(Long id, String nome, String codigoConvite, PapelMembro papel) {

    public static CasaResponse comCodigo(Casa casa, PapelMembro papel) {
        return new CasaResponse(casa.getId(), casa.getNome(), casa.getCodigoConvite(), papel);
    }

    public static CasaResponse semCodigo(Casa casa, PapelMembro papel) {
        return new CasaResponse(casa.getId(), casa.getNome(), null, papel);
    }
}
