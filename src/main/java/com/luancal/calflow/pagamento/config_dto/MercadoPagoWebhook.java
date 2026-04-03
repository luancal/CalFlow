package com.luancal.calflow.pagamento.config_dto;

import lombok.Data;

@Data
public class MercadoPagoWebhook {

    private String action; // "payment.created", "payment.updated"
    private String api_version;
    private WebhookData data;
    private String date_created;
    private Long id;
    private Boolean live_mode;
    private String type; // "payment"
    private String user_id;

    @Data
    public static class WebhookData {
        private String id; // ID do pagamento
    }
}
