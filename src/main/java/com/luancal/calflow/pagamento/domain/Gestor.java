package com.luancal.calflow.pagamento.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "gestores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Gestor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String telefone;

    @Column(name = "pix_chave", nullable = false)
    private String pixChave;

    @Column(name = "codigo_convite", nullable = false, unique = true)
    private String codigoConvite; // Ex: GEST_MARIA_001

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatusGestor status = StatusGestor.ATIVO;
    @Builder.Default
    @Column(name = "data_cadastro", nullable = false)
    private LocalDateTime dataCadastro = LocalDateTime.now();

    // AFILIADOS QUE ESTE GESTOR RECRUTOU
    @OneToMany(mappedBy = "gestor", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Afiliado> afiliadosRecrutados = new ArrayList<>();

    @OneToMany(mappedBy = "gestor", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Comissao> comissoes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (dataCadastro == null) {
            dataCadastro = LocalDateTime.now();
        }
        if (status == null) {
            status = StatusGestor.ATIVO;
        }
    }

    // MÉTODOS DE NEGÓCIO
    public boolean isAtivo() {
        return StatusGestor.ATIVO.equals(this.status);
    }

    public int getTotalAfiliadosAtivos() {
        return (int) afiliadosRecrutados.stream()
                .filter(Afiliado::isAtivo)
                .count();
    }
}
