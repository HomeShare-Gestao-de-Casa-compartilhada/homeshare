package com.homeshare.auth;

import com.homeshare.usuario.Usuario;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/** Gera e valida tokens JWT (jjwt 0.12.x). */
@Service
public class JwtService {

    private final SecretKey chave;
    private final long expiracaoMs;

    public JwtService(@Value("${homeshare.jwt.secret}") String segredo,
                      @Value("${homeshare.jwt.expiracao-minutos}") long expiracaoMinutos) {
        // HS256 exige chave com pelo menos 32 bytes; senão o jjwt lança WeakKeyException na hora
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.expiracaoMs = expiracaoMinutos * 60 * 1000;
    }

    public String gerarToken(Usuario usuario) {
        Date agora = new Date();
        return Jwts.builder()
                .subject(String.valueOf(usuario.getId()))
                .claim("email", usuario.getEmail())
                .issuedAt(agora)
                .expiration(new Date(agora.getTime() + expiracaoMs))
                .signWith(chave)
                .compact();
    }

    /** Devolve o id do usuário se o token for válido (assinatura correta e não expirado). */
    public Optional<Long> extrairUsuarioId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(chave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(Long.parseLong(subject));
        } catch (JwtException | IllegalArgumentException e) {
            // token adulterado, expirado, malformado, etc.
            return Optional.empty();
        }
    }
}
