package com.homeshare.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** O Spring gera a implementação a partir dos nomes dos métodos. */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
