package com.luancal.calflow.pagamento.service;

import com.luancal.calflow.pagamento.domain.*;
import com.luancal.calflow.pagamento.repository.ComissaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ComissaoService {

    private static final Logger log = LoggerFactory.getLogger(ComissaoService.class);

    @Autowired
    private ComissaoRepository comissaoRepository;

    /**
     * Gera comissões automaticamente quando venda é aprovada
     * CRITICAL: Transacional - tudo ou nada
     */
    @Transactional
    public void gerarComissoes(Venda venda) {

        log.info("Gerando comissões: vendaId={}, tipo={}", venda.getId(), venda.getTipo());

        List<Comissao> comissoes = new ArrayList<>();
        boolean isAnual = venda.getValorTotal().compareTo(new BigDecimal("1000.00")) > 0;
        Produto produto = venda.getProduto();

        // 1. COMISSÃO DO AFILIADO (se tiver)
        if (venda.getAfiliado() != null) {
            BigDecimal valorAfiliado;

            if (venda.getTipo() == TipoVenda.IMPLANTACAO) {
                valorAfiliado = produto.getComissaoAfiliadoImplantacao(); // R$ 150
            } else {
                valorAfiliado = produto.getComissaoAfiliadoRecorrente(); // R$ 40
            }

            Comissao comissaoAfiliado = Comissao.builder()
                    .venda(venda)
                    .afiliado(venda.getAfiliado())
                    .tipo(isAnual ? TipoComissao.AFILIADO_IMPLANTACAO : (venda.getTipo() == TipoVenda.IMPLANTACAO ? TipoComissao.AFILIADO_IMPLANTACAO : TipoComissao.AFILIADO_RECORRENTE))
                    .valor(valorAfiliado)
                    .status(StatusComissao.PENDENTE)
                    .dataGeracao(LocalDateTime.now())
                    .build();
            comissoes.add(comissaoAfiliado);

            log.info("Comissão afiliado gerada: afiliado={}",
                    venda.getAfiliado().getNome());
        }

        // 2. COMISSÃO DO GESTOR (se afiliado tiver gestor)
        if (venda.getAfiliado() != null && venda.getAfiliado().getGestor() != null) {
            // Para gestor:
            BigDecimal valorGestor = venda.getTipo() == TipoVenda.IMPLANTACAO
                    ? produto.getComissaoGestorImplantacao()  // R$ 25
                    : produto.getComissaoGestorRecorrente();

                Comissao comissaoGestor = Comissao.builder()
                        .venda(venda)
                        .gestor(venda.getAfiliado().getGestor())
                        .tipo(TipoComissao.GESTOR_IMPLANTACAO)
                        .valor(valorGestor)
                        .status(StatusComissao.PENDENTE)
                        .dataGeracao(LocalDateTime.now())
                        .build();
                comissoes.add(comissaoGestor);

            log.info("Comissão gestor gerada: gestor={}",
                        venda.getAfiliado().getGestor().getNome());
        }

        // 3. Salva todas as comissões de uma vez (transacional)
        if (!comissoes.isEmpty()) {
            comissaoRepository.saveAll(comissoes);
            log.info("Total de comissões geradas: {}", comissoes.size());
        } else {
            log.info("Venda direta - sem comissões a gerar");
        }
    }

    /**
     * Cancela comissões de uma venda (em caso de reembolso)
     */
    @Transactional
    public void cancelarComissoes(String vendaId, String motivo) {

        List<Comissao> comissoes = comissaoRepository.findAll().stream()
                .filter(c -> c.getVenda().getId().equals(vendaId))
                .filter(Comissao::isPendente)
                .toList();

        comissoes.forEach(comissao -> comissao.cancelar(motivo));

        comissaoRepository.saveAll(comissoes);

        log.info("Comissões canceladas: vendaId={}, total={}", vendaId, comissoes.size());
    }

    /**
     * Marca comissão como paga
     */
    @Transactional
    public void marcarComoPaga(String comissaoId, String comprovanteId) {

        Comissao comissao = comissaoRepository.findById(comissaoId)
                .orElseThrow(() -> new RuntimeException("Comissão não encontrada"));

        comissao.marcarComoPaga(comprovanteId);
        comissaoRepository.save(comissao);

        log.info("Comissão paga: id={}, valor={}, comprovante={}",
                comissaoId, comissao.getValor(), comprovanteId);
    }
}
