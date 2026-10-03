package com.homeshare.casa.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Resultado de sair da casa; informa o novo líder quando houve sucessão. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SaidaResponse(String mensagem, MembroResponse novoLider) {
}
