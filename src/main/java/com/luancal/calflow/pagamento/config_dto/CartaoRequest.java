package com.luancal.calflow.pagamento.config_dto;

import lombok.Data;

@Data
public class CartaoRequest {
    private String numero;
    private String cvv;
    private String mesValidade;
    private String anoValidade;
    private String nome;
    private String email;
}
