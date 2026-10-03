package com.homeshare.auth;

import com.homeshare.auth.dto.LoginRequest;
import com.homeshare.auth.dto.RegistroRequest;
import com.homeshare.auth.dto.TokenResponse;
import com.homeshare.exception.ConflitoException;
import com.homeshare.exception.CredenciaisInvalidasException;
import com.homeshare.usuario.Usuario;
import com.homeshare.usuario.UsuarioRepository;
import com.homeshare.usuario.UsuarioResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UsuarioResponse registrar(RegistroRequest requisicao) {
        String email = normalizar(requisicao.email());

        if (usuarioRepository.existsByEmail(email)) {
            throw new ConflitoException("E-mail já cadastrado.");
        }

        // Nunca se guarda a senha: só o hash BCrypt (com "sal" embutido)
        String hash = passwordEncoder.encode(requisicao.senha());
        Usuario usuario = usuarioRepository.save(new Usuario(requisicao.nome().trim(), email, hash));
        return UsuarioResponse.de(usuario);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest requisicao) {
        Usuario usuario = usuarioRepository.findByEmail(normalizar(requisicao.email()))
                .orElseThrow(CredenciaisInvalidasException::new);

        if (!passwordEncoder.matches(requisicao.senha(), usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException(); // mesma mensagem: não revela se o e-mail existe
        }

        return new TokenResponse(jwtService.gerarToken(usuario), "Bearer");
    }

    private String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
