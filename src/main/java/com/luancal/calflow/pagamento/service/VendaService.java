package com.luancal.calflow.pagamento.service;

import com.luancal.calflow.pagamento.config_dto.CheckoutRequest;
import com.luancal.calflow.pagamento.config_dto.CheckoutResponse;
import com.luancal.calflow.pagamento.domain.*;
import com.luancal.calflow.pagamento.repository.*;
import com.mercadopago.resources.payment.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendaService {

    private final VendaRepository vendaRepository;
    private final ClienteRepository clienteRepository;
    private final AfiliadoRepository afiliadoRepository;
    private final ProdutoRepository produtoRepository;
    private final MercadoPagoService mercadoPagoService;
    private final ComissaoService comissaoService;
    private final AssinaturaService assinaturaService;
    private final NotificacaoService notificacaoService;
    private final EmailService emailService;
    private final EvolutionServiceCF evolutionServiceCF;
    private final ClienteCalFlowRepository clienteCalFlowRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CheckoutResponse processarCheckout(CheckoutRequest request) {
        log.info("Iniciando checkout: email={}, afiliado={}",
                request.getEmail(), request.getCodigoAfiliado());

        Cliente cliente = buscarOuCriarCliente(request);

        Produto produto = produtoRepository.findFirstByAtivoTrue()
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        Afiliado afiliado = null;
        if (request.getCodigoAfiliado() != null && !request.getCodigoAfiliado().isBlank()) {
            afiliado = afiliadoRepository.findByCodigoReferencia(request.getCodigoAfiliado())
                    .orElse(null);

            if (afiliado != null && !afiliado.isAtivo()) {
                log.warn("Afiliado inativo: {}", request.getCodigoAfiliado());
                afiliado = null;
            }
        }

        Venda venda = Venda.builder()
                .produto(produto)
                .cliente(cliente)
                .afiliado(afiliado)
                .valorTotal(request.getValor())
                .tipo("anual".equalsIgnoreCase(request.getPlano()) ? TipoVenda.MENSALIDADE : TipoVenda.IMPLANTACAO)
                .status(StatusVenda.PENDENTE)
                .dataVenda(LocalDateTime.now())
                .build();

        venda = vendaRepository.save(venda);

        log.info("Venda criada: id={}, valor={}, afiliado={}",
                venda.getId(), venda.getValorTotal(), afiliado != null ? afiliado.getNome() : "DIRETO");

        try {
            Payment payment = mercadoPagoService.criarPagamentoPix(
                    venda.getId(),
                    venda.getValorTotal(),
                    cliente.getEmail(),
                    cliente.getNome(),
                    cliente.getCpfCnpj()
            );

            venda.setGatewayTransacaoId(payment.getId().toString());
            vendaRepository.save(venda);

            String qrCode = mercadoPagoService.extrairQRCode(payment);
            String qrCodeBase64 = mercadoPagoService.extrairQRCodeBase64(payment);

            return CheckoutResponse.builder()
                    .vendaId(venda.getId())
                    .transactionId(venda.getId())
                    .qrCode(qrCodeBase64)
                    .qrCodeTexto(qrCode)
                    .valor(venda.getValorTotal().toString())
                    .status("pending")
                    .mensagem("Escaneie o QR Code para pagar")
                    .build();

        } catch (Exception e) {
            log.error("Erro ao criar pagamento: vendaId={}", venda.getId(), e);
            throw new RuntimeException("Erro ao processar pagamento. Tente novamente.", e);
        }
    }

    @Transactional
    public void aprovarVenda(String gatewayTransacaoId) {
        log.info("Aprovando venda: mpId={}", gatewayTransacaoId);

        Venda venda = vendaRepository.findByGatewayTransacaoId(gatewayTransacaoId)
                .orElseThrow(() -> new RuntimeException("Venda não encontrada: " + gatewayTransacaoId));

        if (venda.isAprovada()) {
            log.info("Venda já aprovada anteriormente: vendaId={}", venda.getId());
            return;
        }

        venda.aprovar();
        vendaRepository.save(venda);

        log.info("Venda aprovada: vendaId={}, cliente={}, valor={}",
                venda.getId(), venda.getCliente().getNome(), venda.getValorTotal());

        comissaoService.gerarComissoes(venda);

        assinaturaService.criarAssinatura(venda);

        // Criar acesso somente se ainda não existir
        Optional<ClienteCalFlow> clienteExistente = clienteCalFlowRepository.findByEmail(venda.getCliente().getEmail());

        ClienteCalFlow clienteCF;
        String senhaPlana = null;

        if (clienteExistente.isPresent()) {
            clienteCF = clienteExistente.get();
            log.info("ClienteCalFlow já existe: email={}", clienteCF.getEmail());
        } else {
            String usuario = gerarUsuarioUnico(venda.getCliente().getNome());
            senhaPlana = gerarSenhaAleatoria();
            String senhaHash = passwordEncoder.encode(senhaPlana);

            clienteCF = ClienteCalFlow.builder()
                    .usuario(usuario)
                    .senhaHash(senhaHash)
                    .nome(venda.getCliente().getNome())
                    .email(venda.getCliente().getEmail())
                    .telefone(venda.getCliente().getTelefone())
                    .nomeNegocio("Meu Negócio")
                    .tipo("cliente")
                    .status("ativo")
                    .nomeInstancia("calflow_" + usuario)
                    .plano(venda.getTipo() == TipoVenda.IMPLANTACAO ? "mensal" : "anual")
                    .dataVencimento(LocalDate.now().plusMonths(
                            venda.getTipo() == TipoVenda.IMPLANTACAO ? 1 : 12))
                    .dataCriacao(LocalDateTime.now())
                    .build();

            clienteCF = clienteCalFlowRepository.save(clienteCF);
            log.info("ClienteCalFlow criado: usuario={}, email={}", clienteCF.getUsuario(), clienteCF.getEmail());

            try {
                evolutionServiceCF.criarInstancia(clienteCF.getNomeInstancia());
                log.info("Instância Evolution criada: {}", clienteCF.getNomeInstancia());
            } catch (Exception e) {
                log.error("Erro ao criar instância Evolution para cliente={}", clienteCF.getEmail(), e);
            }

            try {
                emailService.enviarCredenciais(
                        clienteCF.getEmail(),
                        clienteCF.getNome(),
                        clienteCF.getUsuario(),
                        senhaPlana
                );
            } catch (Exception e) {
                log.error("Erro ao enviar email de credenciais", e);
            }

            try {
                notificacaoService.enviarCredenciaisWhatsApp(
                        clienteCF.getTelefone(),
                        clienteCF.getNome(),
                        clienteCF.getUsuario(),
                        senhaPlana
                );
            } catch (Exception e) {
                log.error("Erro ao enviar credenciais por WhatsApp", e);
            }
        }

        if (venda.getAfiliado() != null) {
            notificacaoService.notificarAfiliadoVendaAprovada(venda);
        }

        if (venda.getAfiliado() != null && venda.getAfiliado().getGestor() != null) {
            notificacaoService.notificarGestorVendaAprovada(venda);
        }

        try {
            notificacaoService.enviarBoasVindasCliente(venda.getCliente(), "calflow_admin");
        } catch (Exception e) {
            log.error("Erro ao enviar boas-vindas", e);
        }

        log.info("Venda totalmente processada: vendaId={}", venda.getId());
    }

    private Cliente buscarOuCriarCliente(CheckoutRequest request) {
        Optional<Cliente> existente = clienteRepository.findByEmail(request.getEmail().trim().toLowerCase());

        if (existente.isPresent()) {
            log.info("Cliente existente encontrado: email={}", request.getEmail());
            return existente.get();
        }

        Cliente novoCliente = Cliente.builder()
                .nome(request.getNome().trim())
                .email(request.getEmail().trim().toLowerCase())
                .telefone(request.getTelefone().replaceAll("\\D", ""))
                .cpfCnpj(request.getCpfCnpj().replaceAll("\\D", ""))
                .dataCadastro(LocalDateTime.now())
                .build();

        novoCliente = clienteRepository.save(novoCliente);

        log.info("Novo cliente criado: id={}, email={}", novoCliente.getId(), novoCliente.getEmail());

        return novoCliente;
    }

    @Transactional
    public void cancelarVenda(String vendaId, String motivo) {
        Venda venda = vendaRepository.findById(vendaId)
                .orElseThrow(() -> new RuntimeException("Venda não encontrada"));

        if (!venda.isAprovada()) {
            throw new RuntimeException("Apenas vendas aprovadas podem ser canceladas");
        }

        venda.reembolsar(motivo);
        vendaRepository.save(venda);

        comissaoService.cancelarComissoes(vendaId, "Venda reembolsada");

        assinaturaService.cancelarAssinaturaPorVenda(vendaId);

        log.info("Venda cancelada: vendaId={}, motivo={}", vendaId, motivo);
    }

    private String gerarUsuarioUnico(String nome) {
        String base = nome.split(" ")[0]
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");

        if (base.length() < 3) {
            base = "cliente";
        }

        String usuario;
        do {
            int sufixo = 1000 + new SecureRandom().nextInt(9000);
            usuario = base + sufixo;
        } while (clienteCalFlowRepository.findByUsuario(usuario).isPresent());

        return usuario;
    }

    private String gerarSenhaAleatoria() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        return sb.toString();
    }
}