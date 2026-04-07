package com.luancal.calflow.pagamento.controller;

import com.luancal.calflow.pagamento.config_dto.MercadoPagoWebhook;
import com.luancal.calflow.pagamento.service.MercadoPagoService;
import com.luancal.calflow.pagamento.service.VendaService;
import com.mercadopago.resources.payment.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/pagamentos")
@RequiredArgsConstructor
public class WebhookController {

    private final VendaService vendaService;
    private final MercadoPagoService mercadoPagoService;

    @PostMapping("/webhook-mp")
    public ResponseEntity<Void> mercadoPagoWebhook(@RequestBody MercadoPagoWebhook event) {
        try {
            log.info("Webhook recebido: type={}, action={}, id={}",
                    event.getType(), event.getAction(), event.getId());

            // Pagamento único (PIX)
            if ("payment".equalsIgnoreCase(event.getType())) {
                if (event.getData() == null || event.getData().getId() == null) {
                    return ResponseEntity.ok().build();
                }
                Long paymentId = Long.parseLong(event.getData().getId());
                Payment payment = mercadoPagoService.buscarPagamento(paymentId);

                if (payment == null) return ResponseEntity.ok().build();

                if ("approved".equalsIgnoreCase(payment.getStatus())) {
                    vendaService.aprovarVenda(payment.getId().toString());
                }
                return ResponseEntity.ok().build();
            }

            // Assinatura recorrente (mensalidade paga automaticamente)
            if ("preapproval".equalsIgnoreCase(event.getType())) {
                if (event.getData() == null || event.getData().getId() == null) {
                    return ResponseEntity.ok().build();
                }
                // Busca a assinatura e gera comissão recorrente
                vendaService.processarPagamentoRecorrente(event.getData().getId());
                return ResponseEntity.ok().build();
            }

            log.info("Evento ignorado: type={}", event.getType());
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Erro ao processar webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/mercadopago/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Webhook Mercado Pago OK");
    }
}