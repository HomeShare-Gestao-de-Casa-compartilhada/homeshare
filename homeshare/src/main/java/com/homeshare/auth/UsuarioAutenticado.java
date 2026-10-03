package com.homeshare.auth;

/**
 * Quem está fazendo a requisição, extraído do token JWT.
 * Os Controllers recebem isto com @AuthenticationPrincipal.
 */
public record UsuarioAutenticado(Long id, String email) {
}
