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

    @Transactional
    public void gerarComissoes(Venda venda) {
        log.info("Gerando comissões: vendaId={}, tipo={}, valor={}",
                venda.getId(), venda.getTipo(), venda.getValorTotal());

        if (venda.getAfiliado() == null) {
            log.info("Venda direta (sem afiliado) - sem comissões");
            return;
        }

        List<Comissao> comissoes = new ArrayList<>();
        Produto produto = venda.getProduto();

        // Detecta o tipo de venda
        boolean isAnual = venda.getValorTotal()
                .compareTo(new BigDecimal("1000.00")) > 0;
        boolean isImplantacao = venda.getTipo() == TipoVenda.IMPLANTACAO;
        boolean isRecorrente = venda.getTipo() == TipoVenda.MENSALIDADE
                && !isAnual;

        BigDecimal valorAfiliado;
        BigDecimal valorGestor;
        TipoComissao tipoAfiliado;
        TipoComissao tipoGestor;

        if (isAnual) {
            // Plano anual: afiliado R$ 550, gestor R$ 75
            valorAfiliado = produto.getComissaoAfiliadoAnual();
            valorGestor = produto.getComissaoGestorAnual();
            tipoAfiliado = TipoComissao.AFILIADO_IMPLANTACAO;
            tipoGestor = TipoComissao.GESTOR_IMPLANTACAO;

        } else if (isImplantacao) {
            // Implantação PIX: afiliado R$ 150, gestor R$ 25
            valorAfiliado = produto.getComissaoAfiliadoImplantacao();
            valorGestor = produto.getComissaoGestorImplantacao();
            tipoAfiliado = TipoComissao.AFILIADO_IMPLANTACAO;
            tipoGestor = TipoComissao.GESTOR_IMPLANTACAO;

        } else {
            // Mensalidade recorrente: afiliado R$ 40, gestor R$ 0
            valorAfiliado = produto.getComissaoAfiliadoRecorrente();
            valorGestor = BigDecimal.ZERO; // Gestor NÃO ganha recorrente
            tipoAfiliado = TipoComissao.AFILIADO_RECORRENTE;
            tipoGestor = TipoComissao.GESTOR_IMPLANTACAO;
        }

        // Comissão do Afiliado
        if (valorAfiliado != null && valorAfiliado.compareTo(BigDecimal.ZERO) > 0) {
            comissoes.add(Comissao.builder()
                    .venda(venda)
                    .afiliado(venda.getAfiliado())
                    .tipo(tipoAfiliado)
                    .valor(valorAfiliado)
                    .status(StatusComissao.PENDENTE)
                    .dataGeracao(LocalDateTime.now())
                    .build());
            log.info("Comissão afiliado: {} = R$ {}", venda.getAfiliado().getNome(), valorAfiliado);
        }

        // Comissão do Gestor (só implantação e anual, NUNCA recorrente)
        if (venda.getAfiliado().getGestor() != null
                && valorGestor != null
                && valorGestor.compareTo(BigDecimal.ZERO) > 0) {
            comissoes.add(Comissao.builder()
                    .venda(venda)
                    .gestor(venda.getAfiliado().getGestor())
                    .tipo(tipoGestor)
                    .valor(valorGestor)
                    .status(StatusComissao.PENDENTE)
                    .dataGeracao(LocalDateTime.now())
                    .build());
            log.info("Comissão gestor: {} = R$ {}",
                    venda.getAfiliado().getGestor().getNome(), valorGestor);
        }

        if (!comissoes.isEmpty()) {
            comissaoRepository.saveAll(comissoes);
            log.info("Total comissões geradas: {}", comissoes.size());
        }
    }

    @Transactional
    public void cancelarComissoes(String vendaId, String motivo) {
        List<Comissao> comissoes = comissaoRepository.findAll().stream()
                .filter(c -> c.getVenda().getId().equals(vendaId))
                .filter(Comissao::isPendente)
                .toList();

        comissoes.forEach(c -> c.cancelar(motivo));
        comissaoRepository.saveAll(comissoes);
        log.info("Comissões canceladas: vendaId={}, total={}", vendaId, comissoes.size());
    }

    @Transactional
    public void marcarComoPaga(String comissaoId, String comprovanteId) {
        Comissao comissao = comissaoRepository.findById(comissaoId)
                .orElseThrow(() -> new RuntimeException("Comissão não encontrada"));
        comissao.marcarComoPaga(comprovanteId);
        comissaoRepository.save(comissao);
        log.info("Comissão paga: id={}, valor={}", comissaoId, comissao.getValor());
    }
}
