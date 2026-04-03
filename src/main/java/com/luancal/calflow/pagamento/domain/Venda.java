package com.luancal.calflow.pagamento.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vendas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "afiliado_id")
    private Afiliado afiliado; // Pode ser null (venda direta)

    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoVenda tipo; // IMPLANTACAO ou MENSALIDADE

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusVenda status = StatusVenda.PENDENTE;

    @Column(name = "gateway_transacao_id")
    private String gatewayTransacaoId; // ID do Mercado Pago

    @Column(name = "data_venda", nullable = false)
    private LocalDateTime dataVenda = LocalDateTime.now();

    @Column(name = "data_aprovacao")
    private LocalDateTime dataAprovacao;

    @Column(name = "data_cancelamento")
    private LocalDateTime dataCancelamento;

    @Column(name = "motivo_cancelamento", columnDefinition = "TEXT")
    private String motivoCancelamento;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comissao> comissoes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (dataVenda == null) {
            dataVenda = LocalDateTime.now();
        }
        if (status == null) {
            status = StatusVenda.PENDENTE;
        }
    }

    // MÉTODOS DE NEGÓCIO
    public boolean isPendente() {
        return StatusVenda.PENDENTE.equals(this.status);
    }

    public boolean isAprovada() {
        return StatusVenda.APROVADA.equals(this.status);
    }

    public void aprovar() {
        this.status = StatusVenda.APROVADA;
        this.dataAprovacao = LocalDateTime.now();
    }

    public void cancelar(String motivo) {
        this.status = StatusVenda.CANCELADA;
        this.dataCancelamento = LocalDateTime.now();
        this.motivoCancelamento = motivo;
    }

    public void reembolsar(String motivo) {
        this.status = StatusVenda.REEMBOLSADA;
        this.dataCancelamento = LocalDateTime.now();
        this.motivoCancelamento = motivo;
    }
}
