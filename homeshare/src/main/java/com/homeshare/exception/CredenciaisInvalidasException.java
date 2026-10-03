package com.homeshare.exception;

/** Vira HTTP 401 (e-mail ou senha incorretos no login). */
public class CredenciaisInvalidasException extends RuntimeException {
    public CredenciaisInvalidasException() {
        super("E-mail ou senha inválidos.");
    }
}
