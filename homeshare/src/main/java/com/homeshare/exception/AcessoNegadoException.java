package com.homeshare.exception;

/** Vira HTTP 403 (usuário autenticado, mas sem permissão, ex.: não é membro da casa). */
public class AcessoNegadoException extends RuntimeException {
    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
