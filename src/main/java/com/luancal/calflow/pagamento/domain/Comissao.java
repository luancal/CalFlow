package com.luancal.calflow.pagamento.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "comissoes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comissao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "afiliado_id")
    private Afiliado afiliado; // Null se for comissão de gestor

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestor_id")
    private Gestor gestor; // Null se for comissão de afiliado

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoComissao tipo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusComissao status = StatusComissao.PENDENTE;

    @Column(name = "data_geracao", nullable = false)
    private LocalDateTime dataGeracao = LocalDateTime.now();

    @Column(name = "data_pagamento")
    private LocalDateTime dataPagamento;

    @Column(name = "comprovante_pagamento")
    private String comprovantePagamento; // ID da transferência PIX

    @Column(name = "data_cancelamento")
    private LocalDateTime dataCancelamento;

    @Column(name = "motivo_cancelamento", columnDefinition = "TEXT")
    private String motivoCancelamento;

    @PrePersist
    protected void onCreate() {
        if (dataGeracao == null) {
            dataGeracao = LocalDateTime.now();
        }
        if (status == null) {
            status = StatusComissao.PENDENTE;
        }
    }

    // MÉTODOS DE NEGÓCIO
    public boolean isPendente() {
        return StatusComissao.PENDENTE.equals(this.status);
    }

    public void marcarComoPaga(String comprovanteId) {
        this.status = StatusComissao.PAGA;
        this.dataPagamento = LocalDateTime.now();
        this.comprovantePagamento = comprovanteId;
    }

    public void cancelar(String motivo) {
        this.status = StatusComissao.CANCELADA;
        this.dataCancelamento = LocalDateTime.now();
        this.motivoCancelamento = motivo;
    }
}
