package com.luancal.calflow.pagamento.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "produtos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    // VALORES
    @Column(name = "valor_implantacao", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorImplantacao; // R$ 497

    @Column(name = "valor_mensalidade", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorMensalidade; // R$ 197

    // COMISSÕES AFILIADO
    @Column(name = "comissao_afiliado_implantacao", precision = 10, scale = 2)
    private BigDecimal comissaoAfiliadoImplantacao; // R$ 150

    @Column(name = "comissao_afiliado_recorrente", precision = 10, scale = 2)
    private BigDecimal comissaoAfiliadoRecorrente; // R$ 40

    // COMISSÕES GESTOR
    @Column(name = "comissao_gestor_implantacao", precision = 10, scale = 2)
    private BigDecimal comissaoGestorImplantacao; // R$ 25

    @Column(name = "comissao_gestor_recorrente", precision = 10, scale = 2)
    private BigDecimal comissaoGestorRecorrente; // R$ 10
    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;
    @Builder.Default
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Venda> vendas = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }
}
