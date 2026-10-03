package com.homeshare.casa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "casas")
public class Casa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "codigo_convite", nullable = false, unique = true, length = 12)
    private String codigoConvite;

    /** false quando o último morador sai: a casa deixa de aceitar novos moradores. */
    @Column(nullable = false)
    private boolean ativa = true;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    protected Casa() {
    }

    public Casa(String nome, String codigoConvite) {
        this.nome = nome;
        this.codigoConvite = codigoConvite;
        this.criadaEm = Instant.now();
    }

    public void encerrar() {
        this.ativa = false;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getCodigoConvite() { return codigoConvite; }
    public boolean isAtiva() { return ativa; }
    public Instant getCriadaEm() { return criadaEm; }
}
