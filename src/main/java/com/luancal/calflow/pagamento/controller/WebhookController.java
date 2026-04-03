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

            if (!"payment".equalsIgnoreCase(event.getType())) {
                log.info("Evento ignorado: type={}", event.getType());
                return ResponseEntity.ok().build();
            }

            if (event.getData() == null || event.getData().getId() == null) {
                log.warn("Webhook recebido sem data.id");
                return ResponseEntity.ok().build();
            }

            Long paymentId = Long.parseLong(event.getData().getId());
            Payment payment = mercadoPagoService.buscarPagamento(paymentId);

            if (payment == null) {
                log.warn("Pagamento não encontrado no MP: paymentId={}", paymentId);
                return ResponseEntity.ok().build();
            }

            log.info("Pagamento consultado: id={}, status={}, metodo={}",
                    payment.getId(), payment.getStatus(), payment.getPaymentMethodId());

            if ("approved".equalsIgnoreCase(payment.getStatus())) {
                vendaService.aprovarVenda(payment.getId().toString());
                log.info("Venda aprovada via webhook: paymentId={}", paymentId);
            } else {
                log.info("Pagamento ainda não aprovado: paymentId={}, status={}",
                        paymentId, payment.getStatus());
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Erro ao processar webhook Mercado Pago", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/mercadopago/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Webhook Mercado Pago OK");
    }
}