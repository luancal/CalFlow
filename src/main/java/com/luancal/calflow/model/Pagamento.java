package com.luancal.calflow.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
public class Pagamento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String pixCopiaECola;       // Código Pix gerado
    private String pixQrCodeBase64;     // QR Code em base64
    private String mercadoPagoId;       // ID do pagamento no MP
    private String status;              // pending, approved, cancelled
    private BigDecimal valor;

    private LocalDateTime criadoEm;
    private LocalDateTime expiraEm;
    private LocalDateTime pagoEm;

    @ManyToOne
    @JoinColumn(name = "clinica_id")
    private Clinica clinica;

    private String telefoneCliente;

    // Dados do agendamento vinculado
    private String dataAgendamento;     // 2026-02-20
    private String horaAgendamento;     // 14:30
    private String servicoNome;
}
