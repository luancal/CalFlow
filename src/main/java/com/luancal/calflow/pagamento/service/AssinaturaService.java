package com.luancal.calflow.pagamento.service;

import com.luancal.calflow.pagamento.domain.*;
import com.luancal.calflow.pagamento.repository.AssinaturaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class AssinaturaService {

    @Autowired private AssinaturaRepository assinaturaRepository;
    @Autowired private MercadoPagoService mercadoPagoService;
    private static final Logger log = LoggerFactory.getLogger(AssinaturaService.class);

    @Transactional
    public Assinatura criarAssinatura(Venda venda) {
        log.info("Criando assinatura: vendaId={}", venda.getId());

        // Se já veio com preapprovalId do checkout (cartão), usar ele
        String gatewayId = venda.getGatewayTransacaoId();

        // Se NÃO tem (veio do PIX), criar assinatura no MP
        if (venda.getTipo() == TipoVenda.IMPLANTACAO) {
            // PIX pagou implantação, agora cria assinatura recorrente
            gatewayId = mercadoPagoService.criarAssinaturaRecorrente(
                    venda.getCliente().getEmail(),
                    venda.getProduto().getValorMensalidade(),
                    "CalFlow Premium - Mensal"
            );
        }

        Assinatura assinatura = Assinatura.builder()
                .cliente(venda.getCliente())
                .afiliado(venda.getAfiliado())
                .assinaturaGatewayId(gatewayId) // ✅ VINCULADO
                .valor(venda.getProduto().getValorMensalidade())
                .status(StatusAssinatura.ATIVA)
                .dataProximaCobranca(LocalDate.now().plusMonths(1))
                .diaVencimento(LocalDate.now().getDayOfMonth())
                .dataCriacao(LocalDateTime.now())
                .build();

        return assinaturaRepository.save(assinatura);
    }

    /**
     * Cancela assinatura
     */
    @Transactional
    public void cancelarAssinatura(String assinaturaId, String motivo) {

        Assinatura assinatura = assinaturaRepository.findById(assinaturaId)
                .orElseThrow(() -> new RuntimeException("Assinatura não encontrada"));

        assinatura.cancelar(motivo);
        assinaturaRepository.save(assinatura);

        log.info("Assinatura cancelada: id={}, motivo={}", assinaturaId, motivo);
    }

    /**
     * Cancela assinatura por venda (usado em reembolsos)
     */
    @Transactional
    public void cancelarAssinaturaPorVenda(String vendaId) {

        // Busca assinatura vinculada à venda
        assinaturaRepository.findAll().stream()
                .filter(a -> a.isAtiva())
                .filter(a -> a.getCliente().getVendas().stream()
                        .anyMatch(v -> v.getId().equals(vendaId)))
                .findFirst()
                .ifPresent(assinatura -> {
                    assinatura.cancelar("Venda reembolsada");
                    assinaturaRepository.save(assinatura);
                    log.info("Assinatura cancelada por reembolso: vendaId={}", vendaId);
                });
    }
}
