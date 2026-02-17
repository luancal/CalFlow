package com.luancal.calflow.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Entity
@Data
public class EstadoConversa {
    @Id
    private String usuarioTelefone;

    private String bsuid;
    private Integer estadoAtual;
    private String nomePaciente;
    private String dataSugerida;
    @Column(columnDefinition = "TEXT")
    private String horariosTemporarios;
    private Integer paginaHorarios = 0;


    @ManyToOne
    private Clinica clinica;

    @ManyToOne
    private TipoServico servicoSelecionado;

    @ManyToOne
    @JoinColumn(name = "profissional_id")
    private Profissional profissionalSelecionado;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dados_temporarios_usuario", joinColumns = @JoinColumn(name = "usuario_id"))
    @MapKeyColumn(name = "chave_dado")
    @Column(name = "valor_dado")
    private Map<String, String> dadosTemporarios = new HashMap<>();
}