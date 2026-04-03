package com.luancal.calflow.pagamento.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "afiliados")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Afiliado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String telefone;

    @Column(name = "codigo_referencia", nullable = false, unique = true)
    private String codigoReferencia; // Ex: AFF_JOAO_001

    @Column(name = "pix_chave", nullable = false)
    private String pixChave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAfiliado status = StatusAfiliado.ATIVO;

    // RELACIONAMENTO COM GESTOR
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestor_id")
    private Gestor gestor; // Quem recrutou este afiliado

    @Column(name = "data_cadastro", nullable = false)
    private LocalDateTime dataCadastro = LocalDateTime.now();

    @OneToMany(mappedBy = "afiliado", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Venda> vendas = new ArrayList<>();

    @OneToMany(mappedBy = "afiliado", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Comissao> comissoes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (dataCadastro == null) {
            dataCadastro = LocalDateTime.now();
        }
        if (status == null) {
            status = StatusAfiliado.ATIVO;
        }
    }

    // MÉTODOS DE NEGÓCIO
    public boolean isAtivo() {
        return StatusAfiliado.ATIVO.equals(this.status);
    }

    public void ativar() {
        this.status = StatusAfiliado.ATIVO;
    }

    public void desativar() {
        this.status = StatusAfiliado.INATIVO;
    }

    public void bloquear() {
        this.status = StatusAfiliado.BLOQUEADO;
    }
}
