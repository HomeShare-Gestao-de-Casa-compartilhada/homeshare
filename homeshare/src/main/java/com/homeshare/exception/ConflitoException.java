package com.homeshare.exception;

/** Vira HTTP 409 (ex.: e-mail já cadastrado, já é membro da casa). */
public class ConflitoException extends RuntimeException {
    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
