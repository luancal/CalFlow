package com.luancal.calflow.pagamento.config_dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String usuario;
    private String senha;
}