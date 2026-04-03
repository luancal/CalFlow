package com.luancal.calflow.pagamento.config_dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import jakarta.validation.constraints.*;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    private String email;

    @NotBlank(message = "Telefone é obrigatório")
    @Pattern(regexp = "\\d{10,11}", message = "Telefone deve ter 10 ou 11 dígitos")
    private String telefone;

    @NotBlank(message = "CPF/CNPJ é obrigatório")
    private String cpfCnpj;

    private String codigoAfiliado; // Opcional - vem do ?ref=AFF_JOAO_001
    private String plano; // "mensal" ou "anual"
    private BigDecimal valor;
    private String metodo;
}
