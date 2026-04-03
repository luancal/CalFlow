package com.luancal.calflow.pagamento.controller;

import com.luancal.calflow.pagamento.domain.*;
import com.luancal.calflow.pagamento.repository.ComissaoRepository;
import com.luancal.calflow.pagamento.service.NotificacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ProcessadorComissoesJob {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorComissoesJob.class);

    @Autowired
    private ComissaoRepository comissaoRepository;

    @Autowired
    private NotificacaoService notificacaoService;

    @Scheduled(cron = "0 0 10 5 * *") // Segundo Minuto Hora Dia Mês DiaDaSemana
    @Transactional
    public void processarPagamentosMensais() {

        log.info("========================================");
        log.info("INICIANDO PAGAMENTO DE COMISSÕES");
        log.info("========================================");

        // Busca comissões pendentes do mês anterior
        LocalDateTime mesAnterior = LocalDateTime.now().minusMonths(1);
        LocalDateTime inicio = mesAnterior.withDayOfMonth(1).withHour(0).withMinute(0);
        LocalDateTime fim = mesAnterior.withDayOfMonth(mesAnterior.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59);

        List<Comissao> pendentes = comissaoRepository.findByStatusAndPeriodo(
                StatusComissao.PENDENTE, inicio, fim
        );

        if (pendentes.isEmpty()) {
            log.info("Nenhuma comissão pendente para processar");
            return;
        }

        log.info("Total de comissões pendentes: {}", pendentes.size());

        // Agrupa por recebedor (afiliado ou gestor)
        Map<String, List<Comissao>> porRecebedor = pendentes.stream()
                .collect(Collectors.groupingBy(c ->
                        c.getAfiliado() != null
                                ? "AFILIADO_" + c.getAfiliado().getId()
                                : "GESTOR_" + c.getGestor().getId()
                ));

        log.info("Total de recebedores: {}", porRecebedor.size());

        // Processa cada recebedor
        porRecebedor.forEach((recebedorId, comissoes) -> {
            processarPagamento(recebedorId, comissoes);
        });

        log.info("========================================");
        log.info("PAGAMENTO DE COMISSÕES FINALIZADO");
        log.info("========================================");
    }

    /**
     * Processa pagamento de um recebedor específico
     */
    private void processarPagamento(String recebedorId, List<Comissao> comissoes) {

        try {
            // Calcula total
            BigDecimal total = comissoes.stream()
                    .map(Comissao::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Identifica recebedor
            String pixChave;
            String nome;

            if (recebedorId.startsWith("AFILIADO")) {
                Afiliado afiliado = comissoes.get(0).getAfiliado();
                pixChave = afiliado.getPixChave();
                nome = afiliado.getNome();
            } else {
                Gestor gestor = comissoes.get(0).getGestor();
                pixChave = gestor.getPixChave();
                nome = gestor.getNome();
            }

            log.info("Processando pagamento: recebedor={}, total=R$ {}, comissoes={}",
                    nome, total, comissoes.size());

            // Por enquanto simula pagamento
            String comprovanteId = "SIM_" + System.currentTimeMillis();

            // Marca comissões como pagas
            comissoes.forEach(comissao -> {
                comissao.marcarComoPaga(comprovanteId);
            });

            comissaoRepository.saveAll(comissoes);

            // Notifica recebedor
            notificacaoService.notificarPagamentoRealizado(comissoes.get(0), total);

            log.info("✅ Pagamento processado: recebedor={}, total=R$ {}", nome, total);

        } catch (Exception e) {
            log.error("❌ Erro ao processar pagamento: recebedor={}", recebedorId, e);
            // Não quebra o loop - continua processando outros
        }
    }
}
