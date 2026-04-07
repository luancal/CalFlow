package com.luancal.calflow.pagamento.service;

import com.mercadopago.client.common.IdentificationRequest;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest;
import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.client.preapproval.PreapprovalCreateRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.resources.preapproval.Preapproval;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class MercadoPagoService {
    private static final Logger log = LoggerFactory.getLogger(MercadoPagoService.class);
    private final PaymentClient paymentClient;

    @Value("${mercadopago.access.token}")
    private String accessToken;

    public MercadoPagoService() {
        this.paymentClient = new PaymentClient();
    }

    public Payment criarPagamentoPix(String vendaId, BigDecimal valor, String email, String nome, String doc) {
        try {

            String docLimpo = doc.replaceAll("\\D", "");
            String tipoDoc = (docLimpo.length() > 11) ? "CNPJ" : "CPF";

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("venda_id", vendaId);
            metadata.put("sistema", "CalFlow");

            PaymentPayerRequest payer = PaymentPayerRequest.builder()
                    .email(email)
                    .firstName(nome)
                    .identification(IdentificationRequest.builder()
                            .type(tipoDoc)
                            .number(docLimpo)
                            .build())
                    .build();

            PaymentCreateRequest request = PaymentCreateRequest.builder()
                    .transactionAmount(valor)
                    .description("CalFlow - Setup + 1º Mês")
                    .paymentMethodId("pix")
                    .payer(payer)
                    .metadata(metadata)
                    .build();

            return paymentClient.create(request);

        } catch (MPException | MPApiException e) {
            throw new RuntimeException("Erro Mercado Pago: " + e.getMessage());
        }
    }

    //Busca pagamento no Mercado Pago

    public Payment buscarPagamento(Long paymentId) {
        try {
            Payment payment = paymentClient.get(paymentId);
            log.info("Pagamento consultado: id={}, status={}", paymentId, payment.getStatus());
            return payment;

        } catch (MPException | MPApiException e) {
            log.error("Erro ao buscar pagamento: id={}", paymentId, e);
            throw new RuntimeException("Erro ao consultar pagamento", e);
        }
    }

    //Extrai QR Code do pagamento PIX

    public String extrairQRCode(Payment payment) {
        if (payment.getPointOfInteraction() != null &&
                payment.getPointOfInteraction().getTransactionData() != null) {
            return payment.getPointOfInteraction().getTransactionData().getQrCode();
        }
        return null;
    }

    //Extrai QR Code Base64 (imagem)

    public String extrairQRCodeBase64(Payment payment) {
        if (payment.getPointOfInteraction() != null &&
                payment.getPointOfInteraction().getTransactionData() != null) {
            return payment.getPointOfInteraction().getTransactionData().getQrCodeBase64();
        }
        return null;
    }

    public String criarAssinaturaRecorrente(String email, BigDecimal valor, String descricao) {
        try {
            PreapprovalClient client = new PreapprovalClient();
            PreapprovalCreateRequest request = PreapprovalCreateRequest.builder()
                    .reason(descricao)
                    .autoRecurring(PreApprovalAutoRecurringCreateRequest.builder()
                            .frequency(1)
                            .frequencyType("months")
                            .transactionAmount(valor)
                            .currencyId("BRL")
                            .build())
                    .backUrl("https://calflow.pages.dev/cliente.html")
                    .payerEmail(email)
                    .build();

            Preapproval preapproval = client.create(request);
            return preapproval.getId();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criar assinatura: " + e.getMessage());
        }
    }
    public String criarAssinaturaComCartaoToken(String email, BigDecimal valor, String cardTokenId) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);

            Map<String, Object> autoRecurring = new HashMap<>();
            autoRecurring.put("frequency", 1);
            autoRecurring.put("frequency_type", "months");
            autoRecurring.put("transaction_amount", valor);
            autoRecurring.put("currency_id", "BRL");

            Map<String, Object> body = new HashMap<>();
            body.put("reason", "CalFlow Premium - Assinatura Mensal");
            body.put("payer_email", email);
            body.put("back_url", "https://calflow.pages.dev/cliente.html");
            body.put("auto_recurring", autoRecurring);
            body.put("card_token_id", cardTokenId); // Token do cartão vindo do frontend

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://api.mercadopago.com/preapproval",
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            String id = (String) response.getBody().get("id");
            log.info("Assinatura com cartão tokenizado criada: id={}", id);
            return id;

        } catch (Exception e) {
            log.error("Erro ao criar assinatura com cartão", e);
            throw new RuntimeException("Erro ao criar assinatura com cartão: " + e.getMessage());
        }
    }
}