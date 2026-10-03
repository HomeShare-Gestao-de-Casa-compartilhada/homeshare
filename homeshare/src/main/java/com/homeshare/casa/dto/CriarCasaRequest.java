package com.homeshare.casa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarCasaRequest(
        @NotBlank(message = "O nome da casa é obrigatório.")
        @Size(max = 100, message = "O nome da casa deve ter no máximo 100 caracteres.")
        String nome) {
}
