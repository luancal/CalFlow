package com.luancal.calflow.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Clinica {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    // O número de telefone que a clínica usa no WhatsApp (Ex: +553199999999)
    @Column(unique = true)
    private String telefoneDono;

    private String nomeInstancia;
    private String apikeyEvolution;

    private String endereco;
    private Integer tempoServicoMinutos;
    private Integer horarioAbertura = 8;
    private Integer horarioFechamento = 18;
    private int intervaloPadrao = 15;
    private Integer horaAlmocoInicio;
    private Integer horaAlmocoFim;
    private boolean trabalhaSabado;
    private boolean trabalhaDomingo;
    private Integer aberturaSabado;
    private Integer fechamentoSabado;
    @Column(name = "dias_folga")
    private String diasFolga;

    @Column(name = "lembrete_ativo", nullable = false, columnDefinition = "boolean default false")
    private boolean lembreteAtivo = true;
    @Column(length = 500)
    private String mensagemLembrete = "Olá {paciente}, lembrete da sua consulta às {horario}. Confirma?";

    @Column(columnDefinition = "boolean default true")
    private boolean botAtivo = true;

    // Campos para o Google Calendar de cada clínica
    private String googleCalendarId; // O e-mail da agenda
}
