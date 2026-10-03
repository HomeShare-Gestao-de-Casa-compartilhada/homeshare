package com.homeshare.auth;

import com.homeshare.usuario.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Roda antes de cada requisição (equivalente a um middleware / Depends do FastAPI).
 * Se vier "Authorization: Bearer <token>" válido, marca a requisição como autenticada.
 * Se não vier (ou for inválido), apenas segue: quem barra é o SecurityConfig (401).
 *
 * Não é um @Component de propósito: é criado no SecurityConfig para não ser
 * registrado duas vezes pelo Spring Boot.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String cabecalho = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (cabecalho != null && cabecalho.startsWith(PREFIXO)) {
            String token = cabecalho.substring(PREFIXO.length());
            jwtService.extrairUsuarioId(token)
                    .flatMap(usuarioRepository::findById) // token de usuário apagado não vale
                    .ifPresent(usuario -> {
                        UsuarioAutenticado principal = new UsuarioAutenticado(usuario.getId(), usuario.getEmail());
                        UsernamePasswordAuthenticationToken autenticacao =
                                new UsernamePasswordAuthenticationToken(principal, null, List.of());
                        autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(autenticacao);
                    });
        }

        filterChain.doFilter(request, response);
    }
}
