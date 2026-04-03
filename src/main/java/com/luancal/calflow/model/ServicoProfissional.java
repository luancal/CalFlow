package com.luancal.calflow.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Data
@Table(name = "servico_profissional")
public class ServicoProfissional {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "profissional_id")
    private Profissional profissional;

    @ManyToOne
    @JoinColumn(name = "tipo_servico_id")
    private TipoServico tipoServico;

    @Column(precision = 10, scale = 2)
    private BigDecimal precoCustomizado;  // Se null, usa o preço padrão do TipoServico
    private Integer duracaoCustomizada; // Se null, usa duração padrão
    private Boolean ativo = true;
}
