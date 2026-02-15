package com.luancal.calflow.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.luancal.calflow.model.Clinica;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
    public class EventService {

        @Autowired
        private Calendar calendar;
        private final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

        // 1. Gera a lista de horários livres (Ex: ["08:00", "08:30", "10:00"])
        public List<String> isSlotDisponivel(LocalDate data, Clinica clinica, int duracaoServicoMinutos) throws IOException {
            DayOfWeek diaSemana = data.getDayOfWeek();

            // 1. Verifica Sábado/Domingo
            if (diaSemana == DayOfWeek.SATURDAY && !clinica.isTrabalhaSabado()) return new ArrayList<>();
            if (diaSemana == DayOfWeek.SUNDAY && !clinica.isTrabalhaDomingo()) return new ArrayList<>();

            // 2. Verifica Folgas Fixas (Ex: Segunda-feira)
            // Supondo que clinica.getDiasFolga() retorne "1,3" (1=Segunda, 3=Quarta)
            String folgas = String.valueOf(clinica.getDiasFolga());
            if (folgas.contains(String.valueOf(diaSemana.getValue()))) {
                return new ArrayList<>();
            }

            String calendarId = clinica.getGoogleCalendarId();
            List<String> disponiveis = new ArrayList<>();

            // Define limites do dia
            LocalTime abertura = LocalTime.of(clinica.getHorarioAbertura(), 0);
            LocalTime fechamento = LocalTime.of(clinica.getHorarioFechamento(), 0);
            LocalTime almocoInicio = (clinica.getHoraAlmocoInicio() != null) ? LocalTime.of(clinica.getHoraAlmocoInicio(), 0) : null;
            LocalTime almocoFim = (clinica.getHoraAlmocoFim() != null) ? LocalTime.of(clinica.getHoraAlmocoFim(), 0) : null;

            // Busca eventos ocupados no Google
            List<Event> eventosOcupados = listarEventosGoogle(data, calendarId);

            // Loop: Começa na abertura e vai pulando pelo "intervaloPadrao" (ex: 30 em 30 min)
            LocalTime cursor = abertura;
            int passo = clinica.getIntervaloPadrao() > 0 ? clinica.getIntervaloPadrao() : 30;

            while (cursor.plusMinutes(duracaoServicoMinutos).isBefore(fechamento) || cursor.plusMinutes(duracaoServicoMinutos).equals(fechamento)) {
                LocalTime fimSlot = cursor.plusMinutes(duracaoServicoMinutos);

                // Validação 1: Passado (Se for hoje, não mostrar hora que já passou)
                boolean isPassado = data.isEqual(LocalDate.now(ZONE)) && cursor.isBefore(LocalTime.now(ZONE).plusMinutes(10)); // +10min de margem

                // Validação 2: Almoço
                boolean caiNoAlmoco = false;
                if (almocoInicio != null && almocoFim != null) {
                    // Se o serviço começa antes do fim do almoço E termina depois do início do almoço = colisão
                    if (cursor.isBefore(almocoFim) && fimSlot.isAfter(almocoInicio)) {
                        caiNoAlmoco = true;
                    }
                }

                // Validação 3: Google Calendar (Colisão Real)
                boolean ocupadoGoogle = false;
                long slotStartMs = data.atTime(cursor).atZone(ZONE).toInstant().toEpochMilli();
                long slotEndMs = data.atTime(fimSlot).atZone(ZONE).toInstant().toEpochMilli();

                for (Event ev : eventosOcupados) {
                    long evStart = ev.getStart().getDateTime().getValue();
                    long evEnd = ev.getEnd().getDateTime().getValue();
                    // Lógica de colisão de intervalos
                    if (slotStartMs < evEnd && slotEndMs > evStart) {
                        ocupadoGoogle = true;
                        break;
                    }
                }

                if (!isPassado && !caiNoAlmoco && !ocupadoGoogle) {
                    disponiveis.add(cursor.toString());
                }

                cursor = cursor.plusMinutes(passo);
            }

            return disponiveis;
        }

        // Método auxiliar para criar o agendamento
        public void criarAgendamento(String nome, LocalDate data, String telefone, String hora,String calendarId, int duracao, String servico) throws IOException {
            LocalTime time = LocalTime.parse(hora);
            ZonedDateTime inicio = data.atTime(time).atZone(ZONE);
            ZonedDateTime fim = inicio.plusMinutes(duracao);

            Event event = new Event()
                    .setSummary("Consulta: " + nome)
                    .setDescription(servico);

            EventDateTime start = new EventDateTime().setDateTime(new DateTime(inicio.toInstant().toEpochMilli())).setTimeZone("America/Sao_Paulo");
            EventDateTime end = new EventDateTime().setDateTime(new DateTime(fim.toInstant().toEpochMilli())).setTimeZone("America/Sao_Paulo");

            event.setStart(start);
            event.setEnd(end);

            calendar.events().insert(calendarId, event).execute();
        }

        private List<Event> listarEventosGoogle(LocalDate data, String calendarId) throws IOException {
            ZonedDateTime inicioDia = data.atStartOfDay(ZONE);
            ZonedDateTime fimDia = data.atTime(23, 59, 59).atZone(ZONE);

            Events events = calendar.events().list(calendarId)
                    .setTimeMin(new DateTime(inicioDia.toInstant().toEpochMilli()))
                    .setTimeMax(new DateTime(fimDia.toInstant().toEpochMilli()))
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .execute();
            return events.getItems() != null ? events.getItems() : new ArrayList<>();
        }

    public List<Event> buscarAgendamentosPorTelefone(String telefone, String calendarId) throws IOException {
        DateTime agora = new DateTime(System.currentTimeMillis());
        String telefoneLimpo = telefone.replaceAll("[^0-9]", "");

        Events events = calendar.events().list(calendarId)
                .setTimeMin(agora)
                .setMaxResults(20)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();

        List<Event> listaOriginal = events.getItems();
        if (listaOriginal == null) return new ArrayList<>();
        return listaOriginal.stream()
                .filter(e -> {
                    String fullText = (e.getSummary() + " " + e.getDescription()).toLowerCase();
                    return fullText.contains(telefoneLimpo) ||
                            (telefoneLimpo.length() >= 9 && fullText.contains(telefoneLimpo.substring(telefoneLimpo.length() - 9)));
                })
                .collect(Collectors.toList());
    }

    public void cancelarEvento(String eventId, String calendarId) throws IOException {
        calendar.events().delete(calendarId, eventId).execute();
    }
}
