package com.luancal.calflow.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Data
public class TipoServico {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String nome; // Ex: "Corte de Cabelo", "Limpeza Dental"
  @Column(precision = 10, scale = 2)
  private BigDecimal preco; // Ex: 35.00
  private Integer duracaoMinutos; // Ex: 30, 45, 60
  private String descricao; // Ex: "Inclui lavagem"

  @ManyToOne
  @JoinColumn(name = "clinica_id")
  private Clinica clinica; // Relaciona com a clínica dona desse serviço
}