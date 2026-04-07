package com.luancal.calflow.pagamento.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "clientes_calflow")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteCalFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String usuario;

    @Column(nullable = false)
    private String senhaHash;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String telefone;

    @Column(name = "nome_negocio")
    private String nomeNegocio;

    @Column(nullable = false)
    private String tipo; // cliente/admin

    @Column(nullable = false)
    private String status; // ativo/suspenso

    @Column(name = "nome_instancia")
    private String nomeInstancia;

    @Column(name = "plano")
    private String plano;

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    @Column(name = "clinica_id")
    private Long clinicaId;
}