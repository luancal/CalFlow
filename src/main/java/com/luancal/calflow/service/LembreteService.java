package com.luancal.calflow.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.luancal.calflow.model.Clinica;
import com.luancal.calflow.repository.ClinicaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LembreteService {

    private static final Logger logger = LoggerFactory.getLogger(LembreteService.class);

    @Autowired
    private Calendar googleCalendar;

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Autowired
    private EvolutionService evolutionService;

    // CACHE ANTI-DUPLICIDADE: Guarda os IDs dos eventos já avisados nas últimas horas
    // Isso impede que o cliente receba mensagem repetida por causa da sobreposição de horário
    private final Set<String> eventosProcessados = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Limpa o cache a cada 24h para não estourar a memória (Roda toda meia-noite)
    @Scheduled(cron = "0 0 0 * * *")
    public void limparCacheEventos() {
        eventosProcessados.clear();
        logger.info("🧹 Cache de eventos processados limpo.");
    }

    @Scheduled(fixedRate = 600000) // Roda a cada 10 minutos
    public void verificarAgendamentosDeTodasClinicas() {
        logger.info("⏰ Iniciando verificação de lembretes para todas as clínicas...");

        List<Clinica> clinicas = clinicaRepository.findAll();

        for (Clinica clinica : clinicas) {
            // Validações básicas para não perder tempo
            if (clinica.getGoogleCalendarId() == null || !clinica.isLembreteAtivo() || !clinica.isBotAtivo()) {
                continue;
            }

            try {
                processarClinica(clinica);
            } catch (Exception e) {
                // Try-catch DENTRO do loop garante que se uma clínica der erro, as outras continuam funcionando
                logger.error("❌ Erro ao processar clínica {}: {}", clinica.getNome(), e.getMessage());
            }
        }
    }

    private void processarClinica(Clinica clinica) throws IOException {
        long agora = System.currentTimeMillis();

        // Janela de Tempo: Eventos entre 50min e 70min no futuro
        DateTime minTime = new DateTime(agora + (50 * 60 * 1000));
        DateTime maxTime = new DateTime(agora + (70 * 60 * 1000));

        Events events = googleCalendar.events().list(clinica.getGoogleCalendarId())
                .setTimeMin(minTime)
                .setTimeMax(maxTime)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute();

        List<Event> items = events.getItems();

        if (items != null && !items.isEmpty()) {
            for (Event event : items) {
                // VERIFICAÇÃO DE DUPLICIDADE (A chave para não ter reclamação)
                if (eventosProcessados.contains(event.getId())) {
                    continue; // Já avisamos sobre esse evento, pula.
                }

                enviarLembrete(event, clinica);
                eventosProcessados.add(event.getId());
                try {
                    Thread.sleep(2000 + new Random().nextInt(3000)); // Espera 2 a 5 segundos entre cada lembrete
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void enviarLembrete(Event event, Clinica clinica) {
        String descricao = event.getDescription();
        if (descricao == null) return; // Se não tem descrição (telefone), ignora

        // LIMPANDO TELEFONE (Mais robusto)
        // Remove tudo que não é número
        String telefoneBruto = descricao.replaceAll("[^0-9]", "");

        // Validação básica de número BR (DDI 55 + DDD 2 + NUM 8 ou 9 = 12 ou 13 dígitos)
        // Isso evita pegar números errados ou curtos demais
        if (telefoneBruto.length() < 12 || telefoneBruto.length() > 13) {
            logger.warn("⚠️ Número inválido encontrado no evento {}: {}", event.getSummary(), telefoneBruto);
            return;
        }

        String nomePaciente = limparNomePaciente(event.getSummary());
        String dataHoraInicio = event.getStart().getDateTime().toString();
        String horaLegivel = formatarHora(dataHoraInicio);

        // Template da Mensagem
        String msgBase = clinica.getMensagemLembrete();
        if (msgBase == null || msgBase.isEmpty()) {
            msgBase = "Olá {paciente}, passamos para confirmar seu horário hoje às {horario}.";
        }

        String textoFinal = msgBase
                .replace("{paciente}", nomePaciente)
                .replace("{horario}", horaLegivel);

        // Envia
        evolutionService.enviarMensagem(telefoneBruto, textoFinal, clinica);
        logger.info("✅ Lembrete enviado para {} | Clínica: {}", telefoneBruto, clinica.getNome());
    }

    private String formatarHora(String isoDate) {
        try {
            Instant instant = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(isoDate, Instant::from);
            return LocalDateTime.ofInstant(instant, ZoneId.of("America/Sao_Paulo"))
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return "";
        }
    }

    private String limparNomePaciente(String resumo) {
        if (resumo == null) return "Cliente";
        String limpo = resumo;
        limpo = limpo.replace("Consulta:", "")
                .replace("Consulta", "")
                .replace("Agendamento:", "")
                .replace("Paciente:", "");

        // Remove emojis que podem estar no título
        limpo = limpo.replaceAll("[\\p{So}\\p{Cn}]", "");

        if (limpo.contains("-")) limpo = limpo.split("-")[0];
        if (limpo.contains("|")) limpo = limpo.split("\\|")[0];

        return limpo.trim();
    }
}