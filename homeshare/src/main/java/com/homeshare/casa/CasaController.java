package com.homeshare.casa;

import com.homeshare.auth.UsuarioAutenticado;
import com.homeshare.casa.dto.CasaResponse;
import com.homeshare.casa.dto.CriarCasaRequest;
import com.homeshare.casa.dto.EntrarCasaRequest;
import com.homeshare.casa.dto.MembroResponse;
import com.homeshare.casa.dto.SaidaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Só recebe a requisição e delega ao Service; nenhuma regra de negócio aqui. */
@RestController
@RequestMapping("/casas")
public class CasaController {

    private final CasaService casaService;

    public CasaController(CasaService casaService) {
        this.casaService = casaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CasaResponse criar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                              @Valid @RequestBody CriarCasaRequest requisicao) {
        return casaService.criarCasa(usuario.id(), requisicao);
    }

    @PostMapping("/entrar")
    public CasaResponse entrar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                               @Valid @RequestBody EntrarCasaRequest requisicao) {
        return casaService.entrar(usuario.id(), requisicao);
    }

    @GetMapping("/{casaId}/membros")
    public List<MembroResponse> listarMembros(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                              @PathVariable("casaId") Long casaId) {
        return casaService.listarMembros(usuario.id(), casaId);
    }

    @PostMapping("/{casaId}/sair")
    public SaidaResponse sair(@AuthenticationPrincipal UsuarioAutenticado usuario,
                              @PathVariable("casaId") Long casaId) {
        return casaService.sair(usuario.id(), casaId);
    }
}
