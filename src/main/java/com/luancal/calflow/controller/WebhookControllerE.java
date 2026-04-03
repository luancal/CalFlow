package com.luancal.calflow.controller;

import com.luancal.calflow.model.EstadoConversa;
import com.luancal.calflow.model.Pagamento;
import com.luancal.calflow.repository.ClinicaRepository;
import com.luancal.calflow.repository.EstadoConversaRepository;
import com.luancal.calflow.repository.PagamentoRepository;
import com.luancal.calflow.service.EventService;
import com.luancal.calflow.service.EvolutionService;
import com.luancal.calflow.service.PagamentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class WebhookControllerE {

    private static final Logger logger = LoggerFactory.getLogger(WebhookControllerE.class);

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private PagamentoService pagamentoService;

    @Autowired
    private EventService eventService;

    @Autowired
    private EvolutionService evolutionService;

    @Autowired
    private EstadoConversaRepository estadoRepository;

    @Autowired
    private ClinicaRepository clinicaRepository;

    /**
     * Webhook do Mercado Pago
     * Chamado automaticamente quando pagamento é aprovado
     */
    @PostMapping("/mercadopago")
    public ResponseEntity<String> webhookMercadoPago(@RequestBody Map<String, Object> payload) {
        try {
            logger.info("📩 Webhook Mercado Pago recebido: {}", payload);

            // ✅ Extrair dados do webhook
            String action = (String) payload.get("action");

            if ("payment.updated".equals(action) || "payment.created".equals(action)) {
                Map<String, Object> data = (Map<String, Object>) payload.get("data");
                String paymentId = data.get("id").toString();

                // ✅ Consultar status atualizado
                String status = pagamentoService.consultarStatus(paymentId);

                if ("approved".equals(status)) {
                    // ✅ PAGAMENTO APROVADO - Criar agendamento automaticamente
                    processarPagamentoAprovado(paymentId);
                }
            }

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            logger.error("❌ Erro no webhook Mercado Pago: ", e);
            return ResponseEntity.ok("ERROR"); // Retorna OK pra não reprocessar
        }
    }

    /**
     * Processa pagamento aprovado e cria agendamento automaticamente
     */
    private void processarPagamentoAprovado(String mercadoPagoId) {
        try {
            Pagamento pag = pagamentoRepository.findByMercadoPagoId(mercadoPagoId).orElse(null);

            if (pag == null) {
                logger.warn("⚠️ Pagamento não encontrado: {}", mercadoPagoId);
                return;
            }

            if (pag.getDataAgendamento() == null || pag.getHoraAgendamento() == null) {
                logger.warn("⚠️ Dados de agendamento incompletos no pagamento {}", pag.getId());
                return;
            }

            // ✅ Buscar estado da conversa
            EstadoConversa estado = estadoRepository
                    .findById(pag.getTelefoneCliente() + "_" + pag.getClinica().getId())
                    .orElse(null);

            if (estado == null) {
                logger.warn("⚠️ Estado não encontrado para telefone: {}", pag.getTelefoneCliente());
                return;
            }

            // ✅ CRIAR AGENDAMENTO NO GOOGLE CALENDAR
            LocalDate data = LocalDate.parse(pag.getDataAgendamento());
            String horaEscolhida = pag.getHoraAgendamento();
            int duracao = (estado.getServicoSelecionado() != null) ?
                    estado.getServicoSelecionado().getDuracaoMinutos() : 30;

            String tituloEvento = estado.getNomePaciente();
            String descricaoEvento = "Telefone: " + pag.getTelefoneCliente() + " | " +
                    pag.getServicoNome() + " | PAGO";

            String calendarIdFinal;
            if (estado.getProfissionalSelecionado() != null &&
                    estado.getProfissionalSelecionado().getGoogleCalendarId() != null) {
                calendarIdFinal = estado.getProfissionalSelecionado().getGoogleCalendarId();
            } else {
                calendarIdFinal = pag.getClinica().getGoogleCalendarId();
            }

            eventService.criarAgendamento(tituloEvento, data, pag.getTelefoneCliente(),
                    horaEscolhida, calendarIdFinal, duracao, descricaoEvento);

            // ✅ ENVIAR CONFIRMAÇÃO AUTOMÁTICA PARA O CLIENTE
            DateTimeFormatter parser = DateTimeFormatter.ofPattern("HH:mm");
            LocalTime inicioConf = LocalTime.parse(horaEscolhida, parser);
            LocalTime fimConf = inicioConf.plusMinutes(duracao);
            String intervaloHorario = inicioConf.format(parser) + " às " + fimConf.format(parser);

            String mensagemConfirmacao =
                    "✅ *Pagamento Confirmado Automaticamente!*\n" +
                            "Seu agendamento foi confirmado:\n\n" +
                            "📅 *Data:* " + data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "\n" +
                            "🕒 *Horário:* " + intervaloHorario + "\n" +
                            "📝 *Serviço:* " + pag.getServicoNome() + "\n" +
                            "💰 *Valor Pago:* R$ " + String.format("%.2f", pag.getValor()) + "\n" +
                            "📍 *Local:* " + pag.getClinica().getEndereco() + "\n\n" +
                            "Tamo junto! Nos vemos em breve 👊🔥";

            evolutionService.enviarMensagem(pag.getTelefoneCliente(),
                    mensagemConfirmacao, pag.getClinica());

            // ✅ RESETAR ESTADO DO CLIENTE
            estado.setEstadoAtual(0);
            estado.setPaginaHorarios(0);
            estado.setHorariosTemporarios(null);
            estado.setDataSugerida(null);
            estado.setServicoSelecionado(null);
            estado.setProfissionalSelecionado(null);
            estado.getDadosTemporarios().clear();
            estadoRepository.save(estado);

            logger.info("✅ Agendamento criado automaticamente via webhook para {}", pag.getTelefoneCliente());

        } catch (Exception e) {
            logger.error("❌ Erro ao processar pagamento aprovado: ", e);
        }
    }
}
