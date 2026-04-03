package com.luancal.calflow.service;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.resources.payment.PaymentRefund;
import com.luancal.calflow.model.Clinica;
import com.luancal.calflow.model.Pagamento;
import com.luancal.calflow.repository.PagamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PagamentoService {

    @Value("${mercadopago.access_token:#{null}}")
    private String mercadoPagoToken;

    @Autowired
    private PagamentoRepository pagamentoRepository;
    private static final Logger logger = LoggerFactory.getLogger(PagamentoService.class);
    /**
     * Gera pagamento PIX no Mercado Pago
     *
     * @param clinica - Estabelecimento
     * @param telefone - Telefone do cliente
     * @param valor - Valor a cobrar
     * @param descricao - Ex: "Corte Masculino - Barbearia Luann"
     * @return Pagamento com QR Code e código Pix
     */
    public Pagamento gerarPagamentoPix(Clinica clinica, String telefone, BigDecimal valor, String descricao) {
        try {
            // ✅ Configurar Mercado Pago
            MercadoPagoConfig.setAccessToken(
                    clinica.getMercadoPagoToken() != null ?
                            clinica.getMercadoPagoToken() : mercadoPagoToken
            );

            PaymentClient client = new PaymentClient();
            String emailFake = telefone.replaceAll("[^0-9]", "") + "@calflow.app";
            // ✅ Criar requisição de pagamento
            PaymentCreateRequest request = PaymentCreateRequest.builder()
                    .transactionAmount(new BigDecimal(String.valueOf(valor)))
                    .description(descricao)
                    .paymentMethodId("pix")
                    .payer(PaymentPayerRequest.builder()
                            .email(emailFake)  // Pode ser fixo ou pedir ao cliente
                            .build())
                    .build();

            // ✅ Enviar para Mercado Pago
            Payment payment = client.create(request);

            // ✅ Salvar no banco
            Pagamento pag = new Pagamento();
            pag.setMercadoPagoId(payment.getId().toString());
            pag.setStatus(payment.getStatus());
            pag.setValor(valor);
            pag.setClinica(clinica);
            pag.setTelefoneCliente(telefone);
            pag.setCriadoEm(LocalDateTime.now());

            // Expira em 30 minutos
            pag.setExpiraEm(LocalDateTime.now().plusMinutes(30));

            // ✅ Extrair dados do PIX
            if (payment.getPointOfInteraction() != null &&
                    payment.getPointOfInteraction().getTransactionData() != null) {

                String qrCode = payment.getPointOfInteraction()
                        .getTransactionData().getQrCode();
                String qrCodeBase64 = payment.getPointOfInteraction()
                        .getTransactionData().getQrCodeBase64();

                pag.setPixCopiaECola(qrCode);
                pag.setPixQrCodeBase64(qrCodeBase64);
            }

            return pagamentoRepository.save(pag);

        } catch (Exception e) {
            System.err.println("❌ Erro ao gerar pagamento: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    public String consultarStatus(String mercadoPagoId) {
        try {
            PaymentClient client = new PaymentClient();
            Payment payment = client.get(Long.parseLong(mercadoPagoId));

            // Atualiza no banco
            Pagamento pag = pagamentoRepository
                    .findByMercadoPagoId(mercadoPagoId)
                    .orElse(null);

            if (pag != null) {
                pag.setStatus(payment.getStatus());
                if ("approved".equals(payment.getStatus())) {
                    pag.setPagoEm(LocalDateTime.now());
                }
                pagamentoRepository.save(pag);
            }

            return payment.getStatus();
        } catch (Exception e) {
            System.err.println("❌ Erro ao consultar pagamento: " + e.getMessage());
            return "error";
        }
    }

    public boolean reembolsarPagamento(String mercadoPagoId, double percentual) {
        try {
            PaymentClient client = new PaymentClient();
            Payment payment = client.get(Long.parseLong(mercadoPagoId));

            // Calcular valor do reembolso
            BigDecimal valorOriginal = payment.getTransactionAmount();
            BigDecimal valorReembolso = valorOriginal.multiply(new BigDecimal(percentual));

            // Criar refund
            PaymentRefundClient refundClient = new PaymentRefundClient();
            PaymentRefund refund = refundClient.refund(Long.parseLong(mercadoPagoId), valorReembolso);

            logger.info("✅ Reembolso criado: {} - R$ {}", refund.getId(), valorReembolso);
            return true;

        } catch (Exception e) {
            logger.error("❌ Erro ao reembolsar pagamento: ", e);
            return false;
        }
    }
}

