package com.homeshare.usuario;

/** O que a API devolve sobre um usuário (nunca inclui a senha). */
public record UsuarioResponse(Long id, String nome, String email) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
    }
}
