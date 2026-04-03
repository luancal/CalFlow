package com.luancal.calflow.pagamento.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "assinaturas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "afiliado_id")
    private Afiliado afiliado; // Quem vendeu (para comissão recorrente)

    @Column(name = "assinatura_gateway_id")
    private String assinaturaGatewayId; // ID no Mercado Pago

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor; // R$ 197

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAssinatura status = StatusAssinatura.ATIVA;

    @Column(name = "data_proxima_cobranca")
    private LocalDate dataProximaCobranca;

    @Column(name = "dia_vencimento")
    private Integer diaVencimento; // 10, 15, 20...

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao = LocalDateTime.now();

    @Column(name = "data_cancelamento")
    private LocalDateTime dataCancelamento;

    @Column(name = "motivo_cancelamento", columnDefinition = "TEXT")
    private String motivoCancelamento;

    @PrePersist
    protected void onCreate() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
        if (status == null) {
            status = StatusAssinatura.ATIVA;
        }
    }

    // MÉTODOS DE NEGÓCIO
    public boolean isAtiva() {
        return StatusAssinatura.ATIVA.equals(this.status);
    }

    public void cancelar(String motivo) {
        this.status = StatusAssinatura.CANCELADA;
        this.dataCancelamento = LocalDateTime.now();
        this.motivoCancelamento = motivo;
    }
}
