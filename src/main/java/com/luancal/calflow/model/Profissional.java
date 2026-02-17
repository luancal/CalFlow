package com.luancal.calflow.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Profissional {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String googleCalendarId; // Cada profissional tem a sua agenda
    private boolean ativo = true;

    @ManyToOne
    @JoinColumn(name = "clinica_id")
    private Clinica clinica;
}