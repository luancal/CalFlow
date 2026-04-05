package com.luancal.calflow.pagamento.controller;

import com.luancal.calflow.pagamento.config_dto.CheckoutRequest;
import com.luancal.calflow.pagamento.config_dto.CheckoutResponse;
import com.luancal.calflow.pagamento.domain.*;
import com.luancal.calflow.pagamento.repository.AfiliadoRepository;
import com.luancal.calflow.pagamento.repository.ClienteRepository;
import com.luancal.calflow.pagamento.repository.ProdutoRepository;
import com.luancal.calflow.pagamento.repository.VendaRepository;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.common.IdentificationRequest;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest;
import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.client.preapproval.PreapprovalCreateRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preapproval.Preapproval;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final VendaRepository vendaRepository;
    private final AfiliadoRepository afiliadoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;

    @Value("${mercadopago.access.token}")
    private String mercadoPagoAccessToken;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @PostMapping("/processar")
    public ResponseEntity<CheckoutResponse> processar(@Valid @RequestBody CheckoutRequest request) {
        try {
            log.info("Processando checkout: plano={}, metodo={}, valor={}",
                    request.getPlano(), request.getMetodo(), request.getValor());

            if (request.getNome() == null || !request.getNome().trim().matches(".*\\s+.*")) {
                return ResponseEntity.badRequest()
                        .body(CheckoutResponse.error("Digite nome e sobrenome completos"));
            }

            if (request.getPlano() == null ||
                    (!"mensal".equalsIgnoreCase(request.getPlano()) && !"anual".equalsIgnoreCase(request.getPlano()))) {
                return ResponseEntity.badRequest()
                        .body(CheckoutResponse.error("Plano inválido"));
            }

            if (request.getMetodo() == null ||
                    (!"pix".equalsIgnoreCase(request.getMetodo()) && !"cartao".equalsIgnoreCase(request.getMetodo()))) {
                return ResponseEntity.badRequest()
                        .body(CheckoutResponse.error("Método de pagamento inválido"));
            }

            if ("anual".equalsIgnoreCase(request.getPlano()) && "pix".equalsIgnoreCase(request.getMetodo())) {
                return ResponseEntity.badRequest()
                        .body(CheckoutResponse.error("PIX indisponível para o plano anual"));
            }

            MercadoPagoConfig.setAccessToken(mercadoPagoAccessToken);

            Venda venda = criarVenda(request);

            if ("pix".equalsIgnoreCase(request.getMetodo())) {
                return processarPix(venda, request);
            } else {
                return processarCartao(venda, request);
            }

        } catch (Exception e) {
            log.error("Erro ao processar checkout", e);
            return ResponseEntity.internalServerError()
                    .body(CheckoutResponse.error("Erro ao processar pagamento: " + e.getMessage()));
        }
    }

    private Venda criarVenda(CheckoutRequest request) {
        Cliente cliente = clienteRepository.findByEmail(request.getEmail())
                .orElseGet(() -> {
                    Cliente novo = new Cliente();
                    novo.setNome(request.getNome().trim());
                    novo.setEmail(request.getEmail().trim().toLowerCase());
                    novo.setTelefone(request.getTelefone().replaceAll("\\D", ""));
                    novo.setCpfCnpj(request.getCpfCnpj().replaceAll("\\D", ""));
                    return clienteRepository.save(novo);
                });

        Produto produto = produtoRepository.findFirstByAtivoTrue()
                .orElseThrow(() -> new RuntimeException("Nenhum produto ativo cadastrado"));

        Venda venda = new Venda();
        venda.setCliente(cliente);
        venda.setProduto(produto);
        venda.setValorTotal(request.getValor());
        venda.setStatus(StatusVenda.PENDENTE);
        venda.setDataVenda(LocalDateTime.now());

        // Mensal = primeira cobrança de implantação
        // Anual = cobrança única anual
        venda.setTipo("anual".equalsIgnoreCase(request.getPlano())
                ? TipoVenda.MENSALIDADE
                : TipoVenda.IMPLANTACAO);

        if (request.getCodigoAfiliado() != null && !request.getCodigoAfiliado().isBlank()) {
            afiliadoRepository.findByCodigoReferencia(request.getCodigoAfiliado())
                    .ifPresent(venda::setAfiliado);
        }

        return vendaRepository.save(venda);
    }

    private ResponseEntity<CheckoutResponse> processarPix(Venda venda, CheckoutRequest request) {
        try {
            PaymentClient client = new PaymentClient();

            String nomeLimpo = request.getNome().trim();
            String[] partesNome = nomeLimpo.split("\\s+", 2);
            String primeiroNome = partesNome[0];
            String sobrenome = partesNome.length > 1 ? partesNome[1] : "Cliente";

            String documento = request.getCpfCnpj().replaceAll("\\D", "");
            String tipoDocumento = documento.length() > 11 ? "CNPJ" : "CPF";

            PaymentCreateRequest paymentRequest = PaymentCreateRequest.builder()
                    .transactionAmount(request.getValor())
                    .description("CalFlow Premium - Implantação")
                    .paymentMethodId("pix")
                    .payer(PaymentPayerRequest.builder()
                            .email(request.getEmail().trim().toLowerCase())
                            .firstName(primeiroNome)
                            .lastName(sobrenome)
                            .identification(IdentificationRequest.builder()
                                    .type(tipoDocumento)
                                    .number(documento)
                                    .build())
                            .build())
                    .build();

            Payment payment = client.create(paymentRequest);

            venda.setGatewayTransacaoId(payment.getId().toString());
            vendaRepository.save(venda);

            CheckoutResponse response = CheckoutResponse.builder()
                    .vendaId(venda.getId())
                    .transactionId(venda.getId())
                    .qrCode(payment.getPointOfInteraction().getTransactionData().getQrCodeBase64())
                    .qrCodeTexto(payment.getPointOfInteraction().getTransactionData().getQrCode())
                    .status("pending")
                    .valor(request.getValor().toString())
                    .mensagem("PIX gerado com sucesso")
                    .build();

            log.info("PIX gerado com sucesso: vendaId={}, mpId={}", venda.getId(), payment.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao gerar PIX", e);
            throw new RuntimeException("Erro ao gerar PIX: " + e.getMessage());
        }
    }

    private ResponseEntity<CheckoutResponse> processarCartao(Venda venda, CheckoutRequest request) {
        try {
            PreapprovalClient client = new PreapprovalClient();

            BigDecimal valorRecorrente;
            int frequencia;
            String frequencyType;
            String motivo;

            if ("mensal".equalsIgnoreCase(request.getPlano())) {
                valorRecorrente = new BigDecimal("197.00");
                frequencia = 1;
                frequencyType = "months";
                motivo = "CalFlow Premium - Assinatura Mensal";
            } else {
                valorRecorrente = new BigDecimal("2197.00");
                frequencia = 1;
                frequencyType = "years";
                motivo = "CalFlow Premium - Assinatura Anual";
            }

            PreapprovalCreateRequest subscriptionRequest = PreapprovalCreateRequest.builder()
                    .reason(motivo)
                    .autoRecurring(PreApprovalAutoRecurringCreateRequest.builder()
                            .frequency(frequencia)
                            .frequencyType(frequencyType)
                            .transactionAmount(valorRecorrente)
                            .currencyId("BRL")
                            .build())
                    .backUrl(appBaseUrl + "/cliente.html")
                    .payerEmail(request.getEmail().trim().toLowerCase())
                    .build();

            Preapproval subscription = client.create(subscriptionRequest);

            venda.setGatewayTransacaoId(subscription.getId());
            vendaRepository.save(venda);

            CheckoutResponse response = CheckoutResponse.builder()
                    .vendaId(venda.getId())
                    .transactionId(venda.getId())
                    .checkoutUrl(subscription.getInitPoint())
                    .status("pending")
                    .valor(request.getValor().toString())
                    .mensagem("Redirecionando para o Mercado Pago...")
                    .build();

            log.info("Assinatura criada com sucesso: vendaId={}, subscriptionId={}",
                    venda.getId(), subscription.getId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao criar assinatura/cartão", e);
            throw new RuntimeException("Erro ao criar assinatura: " + e.getMessage());
        }
    }

    @GetMapping("/status/{vendaId}")
    public ResponseEntity<?> getStatus(@PathVariable String vendaId) {
        return vendaRepository.findById(vendaId)
                .map(venda -> ResponseEntity.ok(Map.of(
                        "status", venda.getStatus().name().toLowerCase(),
                        "transactionId", venda.getId()
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}