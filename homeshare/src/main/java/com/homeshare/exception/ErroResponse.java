package com.homeshare.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/** Formato único de erro devolvido por toda a API. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResponse(
        Instant timestamp,
        int status,
        String erro,
        String mensagem,
        Map<String, String> campos) {

    public static ErroResponse de(HttpStatus status, String mensagem) {
        return new ErroResponse(Instant.now(), status.value(), status.getReasonPhrase(), mensagem, null);
    }

    public static ErroResponse de(HttpStatus status, String mensagem, Map<String, String> campos) {
        return new ErroResponse(Instant.now(), status.value(), status.getReasonPhrase(), mensagem, campos);
    }
}
