package com.homeshare.casa.dto;

import jakarta.validation.constraints.NotBlank;

public record EntrarCasaRequest(
        @NotBlank(message = "O código de convite é obrigatório.")
        String codigo) {
}
