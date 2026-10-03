package com.homeshare.casa;

import com.homeshare.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Vínculo entre um Usuario e uma Casa (tabela de associação com dados próprios:
 * papel, se está ativo, quando entrou/saiu). É aqui que mora o papel LIDER/MORADOR.
 *
 * Só existe UMA linha por par (usuário, casa). Quem sai fica com ativo=false
 * (histórico preservado) e, se voltar, a mesma linha é reativada.
 */
@Entity
@Table(name = "membros",
        uniqueConstraints = @UniqueConstraint(name = "uk_membro_usuario_casa",
                columnNames = {"usuario_id", "casa_id"}))
public class Membro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "casa_id", nullable = false)
    private Casa casa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PapelMembro papel;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "data_entrada", nullable = false)
    private Instant dataEntrada;

    @Column(name = "data_saida")
    private Instant dataSaida;

    protected Membro() {
    }

    public Membro(Usuario usuario, Casa casa, PapelMembro papel) {
        this.usuario = usuario;
        this.casa = casa;
        this.papel = papel;
        this.dataEntrada = Instant.now();
    }

    /** Sai da casa. Quem sai deixa de ser líder, então a casa nunca fica com 2 líderes. */
    public void sair() {
        this.ativo = false;
        this.dataSaida = Instant.now();
        this.papel = PapelMembro.MORADOR;
    }

    /** Volta para a casa como morador comum; a antiguidade recomeça da nova entrada. */
    public void reativar() {
        this.ativo = true;
        this.papel = PapelMembro.MORADOR;
        this.dataEntrada = Instant.now();
        this.dataSaida = null;
    }

    public void promoverALider() {
        this.papel = PapelMembro.LIDER;
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Casa getCasa() { return casa; }
    public PapelMembro getPapel() { return papel; }
    public boolean isAtivo() { return ativo; }
    public Instant getDataEntrada() { return dataEntrada; }
    public Instant getDataSaida() { return dataSaida; }
}
