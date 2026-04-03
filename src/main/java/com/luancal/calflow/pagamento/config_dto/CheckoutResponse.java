package com.luancal.calflow.pagamento.config_dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {
    private String vendaId;
    private String qrCode;          // PIX: QR Code base64
    private String qrCodeTexto;     // PIX: Código copia e cola
    private String checkoutUrl;     // CARTÃO: URL Mercado Pago ← NOVO
    private String status;
    private String valor;
    private String error;
    private String mensagem;
    private String transactionId;

    public static CheckoutResponse error(String mensagem) {
        return CheckoutResponse.builder()
                .status("error")
                .error(mensagem)
                .build();
    }
}
