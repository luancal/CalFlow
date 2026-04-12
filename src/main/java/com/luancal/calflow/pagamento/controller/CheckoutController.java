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
import java.time.OffsetDateTime;
import java.util.HashMap;
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

    @Value("${app.base.url}")
    private String appBaseUrl;

    // ============================================================
    // PROCESSAR CHECKOUT
    // ============================================================
    @PostMapping("/processar")
    public ResponseEntity<?> processar(@Valid @RequestBody CheckoutRequest request) {
        try {
            log.info("Processando checkout: plano={}, metodo={}, valor={}",
                    request.getPlano(), request.getMetodo(), request.getValor());

            // Validações
            if (request.getNome() == null || !request.getNome().trim().matches(".*\\s+.*")) {
                return ResponseEntity.badRequest().body(CheckoutResponse.error("Digite nome e sobrenome completos"));
            }
            if (request.getPlano() == null ||
                    (!"mensal".equalsIgnoreCase(request.getPlano()) && !"anual".equalsIgnoreCase(request.getPlano()))) {
                return ResponseEntity.badRequest().body(CheckoutResponse.error("Plano inválido"));
            }
            if (request.getMetodo() == null ||
                    (!"pix".equalsIgnoreCase(request.getMetodo()) && !"cartao".equalsIgnoreCase(request.getMetodo()))) {
                return ResponseEntity.badRequest().body(CheckoutResponse.error("Método de pagamento inválido"));
            }
            if ("anual".equalsIgnoreCase(request.getPlano()) && "pix".equalsIgnoreCase(request.getMetodo())) {
                return ResponseEntity.badRequest().body(CheckoutResponse.error("PIX indisponível para o plano anual"));
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

    // ============================================================
    // CRIAR VENDA
    // ============================================================
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
        venda.setTipo("anual".equalsIgnoreCase(request.getPlano())
                ? TipoVenda.MENSALIDADE : TipoVenda.IMPLANTACAO);

        if (request.getCodigoAfiliado() != null && !request.getCodigoAfiliado().isBlank()) {
            afiliadoRepository.findByCodigoReferencia(request.getCodigoAfiliado())
                    .ifPresent(venda::setAfiliado);
        }

        return vendaRepository.save(venda);
    }

    // ============================================================
    // PIX — Gera QR Code de R$ 497
    // ============================================================
    private ResponseEntity<?> processarPix(Venda venda, CheckoutRequest request) {
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
            Cliente cli = venda.getCliente();
            if (cli.getCartaoTokenizado() != null) {
                venda.setCartaoTokenizado(cli.getCartaoTokenizado());
            }
            vendaRepository.save(venda);

            Map<String, Object> response = new HashMap<>();
            response.put("vendaId", venda.getId());
            response.put("transactionId", venda.getId());
            response.put("qrCode", payment.getPointOfInteraction().getTransactionData().getQrCodeBase64());
            response.put("qrCodeTexto", payment.getPointOfInteraction().getTransactionData().getQrCode());
            response.put("status", "pending");
            response.put("valor", request.getValor().toString());
            response.put("mensagem", "PIX gerado com sucesso");

            log.info("PIX gerado: vendaId={}, mpId={}", venda.getId(), payment.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao gerar PIX", e);
            throw new RuntimeException("Erro ao gerar PIX: " + e.getMessage());
        }
    }

    // ============================================================
    // CARTÃO — Checkout Transparente (sem redirect!)
    // Usa o token do cartão para cobrar direto
    // ============================================================
    private ResponseEntity<?> processarCartao(Venda venda, CheckoutRequest request) {
        try {
            PaymentClient paymentClient = new PaymentClient();

            // Pega o token do cartão que o frontend salvou
            Cliente cliente = venda.getCliente();
            String cardToken = cliente.getCartaoTokenizado();

            if (cardToken == null || cardToken.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        CheckoutResponse.error("Cadastre o cartão antes de prosseguir"));
            }

            String documento = request.getCpfCnpj().replaceAll("\\D", "");
            String tipoDocumento = documento.length() > 11 ? "CNPJ" : "CPF";

            if ("mensal".equalsIgnoreCase(request.getPlano())) {
                // ✅ MENSAL: Cobra R$ 497 agora no cartão
                PaymentCreateRequest paymentRequest = PaymentCreateRequest.builder()
                        .transactionAmount(new BigDecimal("497.00"))
                        .token(cardToken)
                        .description("CalFlow Premium - Implantação")
                        .installments(request.getParcelas() != null ? request.getParcelas() : 1)
                        .payer(PaymentPayerRequest.builder()
                                .email(request.getEmail().trim().toLowerCase())
                                .identification(IdentificationRequest.builder()
                                        .type(tipoDocumento)
                                        .number(documento)
                                        .build())
                                .build())
                        .build();

                Payment payment = paymentClient.create(paymentRequest);

                venda.setGatewayTransacaoId(payment.getId().toString());
                vendaRepository.save(venda);

                // ✅ Cria assinatura recorrente de R$ 197/mês (começa no próximo mês)
                PreapprovalClient preapprovalClient = new PreapprovalClient();
                PreapprovalCreateRequest subscriptionRequest = PreapprovalCreateRequest.builder()
                        .reason("CalFlow Premium - Mensalidade R$ 197/mês")
                        .autoRecurring(PreApprovalAutoRecurringCreateRequest.builder()
                                .frequency(1)
                                .frequencyType("months")
                                .transactionAmount(new BigDecimal("197.00"))
                                .currencyId("BRL")
                                .startDate(OffsetDateTime.now().plusMonths(1))
                                .build())
                        .backUrl(appBaseUrl + "/cliente.html")
                        .payerEmail(request.getEmail().trim().toLowerCase())
                        .build();

                Preapproval subscription = preapprovalClient.create(subscriptionRequest);

                Map<String, Object> response = new HashMap<>();
                response.put("vendaId", venda.getId());
                response.put("transactionId", venda.getId());
                response.put("status", payment.getStatus());
                response.put("valor", "497.00");
                response.put("mensagem", "Pagamento de R$ 497 processado! Assinatura de R$ 197/mês criada.");
                response.put("paymentStatus", payment.getStatus());
                response.put("subscriptionId", subscription.getId());

                log.info("Mensal cartão transparente: pagamento 497 status={}, assinatura={}, vendaId={}",
                        payment.getStatus(), subscription.getId(), venda.getId());
                return ResponseEntity.ok(response);

            } else {
                // ✅ ANUAL: Cobra R$ 2197 agora + renova todo ano automaticamente
                PaymentCreateRequest paymentRequest = PaymentCreateRequest.builder()
                        .transactionAmount(new BigDecimal("2197.00"))
                        .token(cardToken)
                        .description("CalFlow Premium - Plano Anual")
                        .installments(request.getParcelas())
                        .payer(PaymentPayerRequest.builder()
                                .email(request.getEmail().trim().toLowerCase())
                                .identification(IdentificationRequest.builder()
                                        .type(tipoDocumento)
                                        .number(documento)
                                        .build())
                                .build())
                        .build();

                Payment payment = paymentClient.create(paymentRequest);
                venda.setGatewayTransacaoId(payment.getId().toString());
                vendaRepository.save(venda);

                // 2. Cria assinatura recorrente anual (renova todo ano)
                PreapprovalClient preapprovalClient = new PreapprovalClient();
                PreapprovalCreateRequest subscriptionRequest = PreapprovalCreateRequest.builder()
                        .reason("CalFlow Premium - Plano Anual R$ 2.197/ano")
                        .autoRecurring(PreApprovalAutoRecurringCreateRequest.builder()
                                .frequency(1)
                                .frequencyType("years")
                                .transactionAmount(new BigDecimal("2197.00"))
                                .currencyId("BRL")
                                .startDate(OffsetDateTime.now().plusYears(1))
                                .build())
                        .backUrl(appBaseUrl + "/cliente.html")
                        .payerEmail(request.getEmail().trim().toLowerCase())
                        .build();

                Preapproval subscription = preapprovalClient.create(subscriptionRequest);

                Map<String, Object> response = new HashMap<>();
                response.put("vendaId", venda.getId());
                response.put("transactionId", venda.getId());
                response.put("status", payment.getStatus());
                response.put("valor", "2197.00");
                response.put("mensagem", "Pagamento anual processado! Renovação automática criada.");
                response.put("paymentStatus", payment.getStatus());
                response.put("subscriptionId", subscription.getId());

                log.info("Anual: pagamento 2197 + assinatura anual criada: vendaId={}", venda.getId());
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            log.error("Erro ao processar cartão transparente", e);
            throw new RuntimeException("Erro ao processar cartão: " + e.getMessage());
        }
    }

    // ============================================================
    // STATUS
    // ============================================================
    @GetMapping("/status/{vendaId}")
    public ResponseEntity<?> getStatus(@PathVariable String vendaId) {
        return vendaRepository.findById(vendaId)
                .map(venda -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", venda.getStatus().name().toLowerCase());
                    response.put("transactionId", venda.getId());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ============================================================
    // SALVAR TOKEN DO CARTÃO (vem do frontend)
    // ============================================================
    @PostMapping("/salvar-token-cartao")
    public ResponseEntity<?> salvarTokenCartao(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String token = request.get("token");

            if (email == null || token == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Email e token são obrigatórios");
                return ResponseEntity.badRequest().body(error);
            }

            Cliente cliente = clienteRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

            cliente.setCartaoTokenizado(token);
            clienteRepository.save(cliente);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Token salvo");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao salvar token", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}