package com.luancal.calflow.service;

import com.google.api.services.calendar.model.Event;
import com.luancal.calflow.controller.WhatsAppController;
import com.luancal.calflow.model.*;
import com.luancal.calflow.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WhatsAppService {

    private final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    @Autowired
    private ClinicaRepository clinicaRepository;
    @Autowired
    private EstadoConversaRepository estadoRepository;
    @Autowired
    private TipoServicoRepository servicoRepository;
    @Autowired
    private ProfissionalRepository profissionalRepository;
    @Autowired
    private ServicoProfissionalRepository servicoProfissionalRepository;
    @Autowired
    private PagamentoService pagamentoService;
    @Autowired
    private PagamentoRepository pagamentoRepository;
    @Autowired
    private EvolutionService metaService;
    @Autowired
    private EventService eventService;

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppService.class);
    private final long startTime = Instant.now().getEpochSecond();

    @Async
    public void processarMensagemAsync(Map<String, Object> payload, String messageId) {
        try {
            Map<String, Object> data = (Map) payload.get("data");
            Map<String, Object> key = (Map) data.get("key");

            Map<String, Object> message = (Map) data.get("message");

            // 2. Extração do Texto
            String texto = null;
            if (message != null) {
                if (message.containsKey("conversation")) {
                    texto = (String) message.get("conversation");
                } else if (message.containsKey("extendedTextMessage")) {
                    Map<String, Object> extended = (Map) message.get("extendedTextMessage");
                    if (extended != null) texto = (String) extended.get("text");
                }
            }

            boolean fromMe = (boolean) key.get("fromMe");

            String remoteJid = (String) key.get("remoteJid");
            String instanceName = (String) payload.get("instance");
            Clinica clinica = clinicaRepository.findByNomeInstancia(instanceName).orElse(null);
            if (clinica == null) return;
            String bsuid = null;
            if (data.containsKey("bsuid")) {
                bsuid = (String) data.get("bsuid");
            }
            long messageTimestamp = Long.parseLong(data.get("messageTimestamp").toString());

            if (messageTimestamp < startTime) {
                logger.warn("⏩ Ignorando mensagem antiga (Backlog).");
                return;
            }

            if (texto == null || texto.trim().isEmpty()) {
                metaService.enviarMensagem(remoteJid, "Sinto muito, eu ainda não consigo entender áudios ou imagens. 😕\nPor favor, *digite* o que você precisa.", clinica);
                return;
            }

            String chaveUnica = remoteJid + "_" + clinica.getId();

            logger.info("📩 MENSAGEM RECEBIDA | Instância: {} | De: {} | Texto: {}", instanceName, remoteJid, texto);

            EstadoConversa estado = estadoRepository.findById(chaveUnica).orElse(null);

            if (estado == null || !estado.getClinica().getId().equals(clinica.getId())) {
                estado = new EstadoConversa();
                estado.setUsuarioTelefone(chaveUnica);
                estado.setBsuid(bsuid);
                estado.setClinica(clinica);
                estado.setEstadoAtual(0);
                estado = estadoRepository.save(estado);
            } else if (bsuid != null && estado.getBsuid() == null) {
                estado.setBsuid(bsuid);
            }

            if (fromMe) {
                if (texto != null && texto.toLowerCase().contains("#assumir")) {
                    if (estado != null) {
                        estado.setEstadoAtual(5);
                        estadoRepository.save(estado);
                        metaService.enviarMensagem(remoteJid, "✅ Entendido! Alguém assumiu o atendimento.\nPara me reativar, o cliente deve digitar *reiniciar*.", clinica);
                    }
                    return;
                } else {
                    return;
                }
            }

            if (clinica.getNome().equalsIgnoreCase("Barbearia Luis")) {
                executarLogicaBarbeiroLuis(remoteJid, texto, clinica);
            } else {
                executarLogicaCoringaAgenda(remoteJid, texto, clinica, estado);
            }

            logger.info("✅ Lógica processada com sucesso para {}", remoteJid);

        } catch (Exception e) {
            logger.error("❌ ERRO CRÍTICO no Webhook: ", e);
        }
    }

    private void executarLogicaBarbeiroLuis(String telefone, String texto, Clinica clinica) {
        String msg = texto.toLowerCase();
        if (msg.contains("preço") || msg.contains("valor")) {
            metaService.enviarMensagem(telefone, "💈 Cortes: R$ 30,00 | Barba: R$ 20,00", clinica);
        } else if (msg.contains("onde") || msg.contains("endereço")) {
            metaService.enviarMensagem(telefone, "📍 Rua das Flores, 123 - Centro.", clinica);
        } else {
            metaService.enviarMensagem(telefone, "E aí, beleza? Sou o João Barbeiro. Pergunte sobre 'preço' ou 'endereço'.", clinica);
        }
    }

    private void executarLogicaCoringaAgenda(String de, String mensagem, Clinica clinica, EstadoConversa estado) {

        String msgLimpa = mensagem.trim().toLowerCase();
        String calendarId = clinica.getGoogleCalendarId();

        if (msgLimpa.matches("ok|certo|blz|beleza|fechado|combinado|confirmado|confirmar|obrigado|obrigada|vlw|valeu|obg|obgd|jae|fechou|estarei la|vou atrasar")) {
            metaService.enviarMensagem(de, "Combinado! 👍 Já registrei seu retorno aqui. Estaremos te aguardando!", clinica);
            return;
        }

        if (msgLimpa.equalsIgnoreCase("reiniciar") || msgLimpa.equalsIgnoreCase("menu")) {
            estado.setEstadoAtual(2);
            estado.setServicoSelecionado(null);
            estado.setPaginaHorarios(0);
            estado.setServicoSelecionado(null);
            estado.setProfissionalSelecionado(null);
            estado.getDadosTemporarios().clear();
            estadoRepository.save(estado);
            metaService.enviarMensagem(de, "🔄 Menu Principal:\n\n1. ✅ Novo Agendamento\n2. ❌ Cancelar ou Remarcar\n3. 📞 Informações e Preços\n4. 💬 Falar com atendente\n\nCaso precise, digite *menu* para voltar aqui", clinica);

            return;
        }

        int estadoAtual = estado.getEstadoAtual();

        if (estadoAtual == 5) {
            return; // Modo silêncioso (Atendente Humano)
        }

        try {
            switch (estadoAtual) {
                case 0:
                    if (estado.getNomePaciente() != null) {
                        metaService.enviarMensagem(de, "Olá " + estado.getNomePaciente() + ", que bom te ver de volta! Como posso ajudar?\n\n1. ✅ Agendar consulta\n2. 🔄 Meus Agendamentos (Cancelar/Remarcar)\n3. 📞 Informações e Preços\n4. 💬 Falar com Recepção\n\nCaso precise, digite *menu* para voltar aqui", clinica);
                        estado.setEstadoAtual(2);
                        estadoRepository.save(estado);
                    } else {
                        metaService.enviarMensagem(de, "E aí! 👊\n" + "Bem-vindo à " + clinica.getNome() + "\n" + "Vou te ajudar a agendar seu horário de forma rápida.\n" + "Pra começar, qual é o seu *nome*?", clinica);
                        estado.setEstadoAtual(1);
                        estadoRepository.save(estado);
                    }
                    break;

                case 1:
                    String nomeFormatado = formatarNome(msgLimpa);
                    estado.setNomePaciente(nomeFormatado);
                    metaService.enviarMensagem(de, "Obrigado, " + nomeFormatado + "! Como posso te ajudar hoje?\n\n1. ✅ Agendar consulta\n2. 🔄 Meus Agendamentos (Cancelar/Remarcar)\n3. 📞 Informações e Preços\n4. 💬 Falar com Recepção\n\nCaso precise, digite *menu* para voltar aqui", clinica);
                    estado.setEstadoAtual(2);
                    estadoRepository.save(estado);
                    break;

                case 2:
                    if (msgLimpa.equals("1")) {
                        List<TipoServico> servicos = servicoRepository.findByClinicaId(clinica.getId());
                        List<Profissional> profissionais = profissionalRepository.findByClinicaIdAndAtivoTrue(clinica.getId());
                        if (servicos.isEmpty()) {
                            if (profissionais.size() <= 1) {
                                if (!profissionais.isEmpty()) {
                                    estado.setProfissionalSelecionado(profissionais.get(0));
                                }
                                // FLUXO ANTIGO (Sem escolha de serviço)
                                metaService.enviarMensagem(de, "Certo! Agora digite a data desejada.\nExemplo: *20/01* ou *Sexta*", clinica);
                                estado.setEstadoAtual(3); // Vai direto pedir data
                                estadoRepository.save(estado);
                            } else {
                                // ✅ Múltiplos profissionais → mostra lista
                                StringBuilder sb = new StringBuilder("✂️ *Com qual profissional deseja agendar?*\n\n");
                                for (int i = 0; i < profissionais.size(); i++) {
                                    sb.append(i + 1).append(". ").append(profissionais.get(i).getNome()).append("\n");
                                }
                                sb.append("\nDigite o *número* da sua escolha:");
                                metaService.enviarMensagem(de, sb.toString(), clinica);
                                estado.setEstadoAtual(11);
                            }
                        } else {
                            // FLUXO NOVO (Escolher serviço)
                            StringBuilder menuServicos = new StringBuilder("🏆 *Nossos Serviços*\n\n");
                            menuServicos.append("Por favor, escolha o serviço que deseja agendar digitando o *NÚMERO* correspondente:\n\n");
                            for (int i = 0; i < servicos.size(); i++) {
                                TipoServico s = servicos.get(i);
                                String precoFormatado = String.format("%.2f", s.getPreco()).replace(".", ",");
                                menuServicos.append(i + 1).append(". *").append(s.getNome()).append("* - R$ ").append(precoFormatado).append("\n");
                            }
                            metaService.enviarMensagem(de, menuServicos.toString(), clinica);
                            estado.setEstadoAtual(10); // NOVO ESTADO: Escolhendo Serviço
                            estadoRepository.save(estado);
                        }
                    } else if (msgLimpa.equals("2")) {
                        // Lógica de Buscar Agendamentos para Cancelar/Remarcar
                        List<Event> agendamentos = eventService.buscarAgendamentosPorTelefone(de, calendarId);
                        if (agendamentos.isEmpty()) {
                            metaService.enviarMensagem(de, "🔍 Não encontrei agendamentos futuros vinculados ao seu número.\nDeseja agendar um novo? Digite *1*.", clinica);
                        } else {
                            StringBuilder sb = new StringBuilder("Seus agendamentos:\n");
                            int index = 1;
                            for (Event evt : agendamentos) {
                                String inicio = evt.getStart().getDateTime().toString();
                                OffsetDateTime odt = OffsetDateTime.parse(inicio);
                                sb.append(index).append(". ").append(odt.format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm"))).append("\n");
                                estado.getDadosTemporarios().put("cancel_id_" + index, evt.getId());
                                index++;
                            }
                            sb.append("\nDigite o *número* do agendamento para CANCELAR ou REMARCAR (Ex: 1):");
                            metaService.enviarMensagem(de, sb.toString(), clinica);
                            estado.setEstadoAtual(6);
                            estadoRepository.save(estado);
                        }
                    } else if (msgLimpa.equals("3")) {
                        List<TipoServico> servicos = servicoRepository.findByClinicaId(clinica.getId());
                        StringBuilder info = new StringBuilder();
                        info.append("👑 *").append(clinica.getNome()).append("* 👑\n\n");
                        info.append("📍 *ENDEREÇO*\n");
                        info.append(clinica.getEndereco()).append("\n\n");
                        info.append("🕐 *HORÁRIOS DE ATENDIMENTO*\n");
                        info.append(gerarTextoHorario(clinica)).append("\n");
                        info.append("✨ *NOSSOS SERVIÇOS*\n");
                        for(TipoServico s : servicos) {
                            info.append("• ").append(s.getNome())
                                    .append(" - *R$ ").append(String.format("%.2f", s.getPreco())).append("*\n");
                        }
                        info.append("\n👉 Digite *1* para Agendar\n");
                        info.append("👉 Digite *menu* para voltar");
                        metaService.enviarMensagem(de, info.toString(), clinica);

                    } else if (msgLimpa.equals("4")) {
                        metaService.enviarMensagem(de, "Entendido. A recepção irá te atender em breve. Ficarei mudo agora.\n(Para me reativar, digite *reiniciar*)", clinica);
                        estado.setEstadoAtual(5);
                        estadoRepository.save(estado);
                    } else {
                        metaService.enviarMensagem(de, "Opção inválida. Digite de 1 a 4. Ou *menu*", clinica);
                    }
                    break;

                case 3:
                    try {
                        LocalDate data = interpretarData(msgLimpa);
                        LocalDate hoje = LocalDate.now();
                        DayOfWeek dia = data.getDayOfWeek();
                        if ((dia == DayOfWeek.SATURDAY && !clinica.isTrabalhaSabado()) ||
                                (dia == DayOfWeek.SUNDAY && !clinica.isTrabalhaDomingo())) {
                            metaService.enviarMensagem(de, "❌ Não atendemos neste dia da semana. Por favor, escolha outro.", clinica);
                            return;
                        }

                        if (data.isBefore(hoje)) {
                            metaService.enviarMensagem(de, "❌ Não é possível agendar em datas passadas. Por favor, escolha hoje ou uma data futura.", clinica);
                            return;
                        }

                        int duracao = (estado.getServicoSelecionado() != null) ? estado.getServicoSelecionado().getDuracaoMinutos() : 30;
                        String calendarIdParaBuscar;
                        if (estado.getProfissionalSelecionado() != null &&
                                estado.getProfissionalSelecionado().getGoogleCalendarId() != null) {
                            calendarIdParaBuscar = estado.getProfissionalSelecionado().getGoogleCalendarId();} else {calendarIdParaBuscar = clinica.getGoogleCalendarId();}
                        List<String> horarios = eventService.isSlotDisponivel(data, clinica, duracao, calendarIdParaBuscar);
                        if (horarios.isEmpty()) {
                            metaService.enviarMensagem(de, "😔 Infelizmente não há horários disponíveis para este dia. Por favor, tente outra data! ou digite *menu*.", clinica);
                            return;
                        }
                        int offset = 10;
                        List<String> exibicao = horarios.stream()
                                .limit(offset)
                                .collect(Collectors.toList());

                            StringBuilder sb = new StringBuilder("✨ *Ótima escolha!* Veja os horários disponíveis para *" + data.format(DateTimeFormatter.ofPattern("dd/MM")) + "*:\n\n");
                            DateTimeFormatter parser = DateTimeFormatter.ofPattern("HH:mm");
                            for (int i = 0; i < exibicao.size(); i++) {
                                String horaInicioStr = exibicao.get(i);
                                LocalTime inicio = LocalTime.parse(horaInicioStr, parser);

                                LocalTime fim = inicio.plusMinutes(duracao);

                                // Formato: 1. 08:00 - 08:30
                                sb.append(i + 1).append(". ").append(horaInicioStr)
                                        .append(" - ").append(fim.format(parser)).append("\n");
                            }

                            sb.append("\nDigite o *NÚMERO* do horário desejado para agendar (Ex: 3) ");

                            if (horarios.size() > offset) {
                                sb.append("\nDigite *+* para ver mais horários. ");
                            }
                            sb.append("Ou digite uma nova data (ex: Quarta ou 18/02) se preferir outro dia.");
                            metaService.enviarMensagem(de, sb.toString(), clinica);

                        estado.setHorariosTemporarios(String.join(",", exibicao));
                        estado.setEstadoAtual(4);
                        estado.setDataSugerida(data.toString());
                        estado.setPaginaHorarios(0);
                        estadoRepository.save(estado);
                    } catch (Exception e) {
                        metaService.enviarMensagem(de, "Não entendi a data. Tente nestes modelos: 'Amanhã', 'Segunda' ou '20/02'. Se nada funcionar digite *menu*", clinica);
                    }
                    break;

                case 4:
                    try {
                        if (estado.getHorariosTemporarios() == null || estado.getDataSugerida() == null) {
                            metaService.enviarMensagem(de, "Sessão expirada. Comece de novo.", clinica);
                            estado.setEstadoAtual(0);
                            estadoRepository.save(estado);
                            break;
                        }
                        if (msgLimpa.equals("+")) {
                            LocalDate dataSelecionada = LocalDate.parse(estado.getDataSugerida());
                            int duracao = (estado.getServicoSelecionado() != null) ? estado.getServicoSelecionado().getDuracaoMinutos() : 30;
                            String calendarIdParaBuscar;
                            if (estado.getProfissionalSelecionado() != null && estado.getProfissionalSelecionado().getGoogleCalendarId() != null) {calendarIdParaBuscar = estado.getProfissionalSelecionado().getGoogleCalendarId();} else {calendarIdParaBuscar = clinica.getGoogleCalendarId();}
                            List<String> todosHorarios = eventService.isSlotDisponivel(dataSelecionada, clinica, duracao, calendarIdParaBuscar);
                            if (todosHorarios.isEmpty()) {
                                metaService.enviarMensagem(de, "😔 Não há mais horários disponíveis para este dia.", clinica);
                                return;
                            }
                            int paginaAtual = (estado.getPaginaHorarios() != null) ? estado.getPaginaHorarios() : 0;
                            int novaPagina = paginaAtual + 1;
                            int offset = 10;

                            List<String> exibicao = todosHorarios.stream()
                                    .skip((long) novaPagina * offset)
                                    .limit(offset)
                                    .collect(Collectors.toList());
                            if (exibicao.isEmpty()) {
                                metaService.enviarMensagem(de, "📋 Você já viu todos os horários disponíveis para este dia.\nDigite uma nova data (ex: Amanhã, 22/08) ou *menu*.", clinica);
                                return;
                            }

                            StringBuilder sb = new StringBuilder("✨ *Ótima escolha!* Veja os horários disponíveis para *" + dataSelecionada.format(DateTimeFormatter.ofPattern("dd/MM")) + "* (página " + (novaPagina + 1) + "):\n\n");
                            DateTimeFormatter parser = DateTimeFormatter.ofPattern("HH:mm");
                            for (int i = 0; i < exibicao.size(); i++) {
                                String horaInicioStr = exibicao.get(i);
                                LocalTime inicio = LocalTime.parse(horaInicioStr, parser);
                                LocalTime fim = inicio.plusMinutes(duracao);
                                sb.append(i + 1).append(". ").append(horaInicioStr)
                                        .append(" - ").append(fim.format(parser)).append("\n");
                            }
                            sb.append("\nDigite o *NÚMERO* do horário desejado para agendar (Ex: 3) ");

                            if (todosHorarios.size() > (novaPagina + 1) * offset) {
                                sb.append("\nDigite *+* para ver mais horários. ");
                            }
                            sb.append("Ou digite uma nova data (ex: Quarta ou 18/02) se preferir outro dia.");
                            metaService.enviarMensagem(de, sb.toString(), clinica);
                            estado.setPaginaHorarios(novaPagina);
                            estado.setHorariosTemporarios(String.join(",", exibicao));
                            estadoRepository.save(estado);
                            return;
                        }

                        if (msgLimpa.matches("\\d+")) {
                            String[] lista = estado.getHorariosTemporarios().split(",");
                            int escolha = Integer.parseInt(msgLimpa) - 1;

                        if (escolha >= 0 && escolha < lista.length) {
                            String horaEscolhida = lista[escolha];
                            LocalDate data = LocalDate.parse(estado.getDataSugerida());
                            int duracao = (estado.getServicoSelecionado() != null) ? estado.getServicoSelecionado().getDuracaoMinutos() : 30;

                            String tituloEvento = estado.getNomePaciente();
                            String servicoNome = (estado.getServicoSelecionado() != null) ? estado.getServicoSelecionado().getNome() : "Consulta";

                            if (clinica.getCobrancaAntecipada() != null &&
                                    clinica.getCobrancaAntecipada() &&
                                    estado.getServicoSelecionado() != null) {

                                // ✅ GERAR PAGAMENTO PIX
                                BigDecimal valor = estado.getServicoSelecionado().getPreco();
                                String descricao = servicoNome + " - " + clinica.getNome();

                                Pagamento pag = pagamentoService.gerarPagamentoPix(
                                        clinica, de, valor, descricao
                                );

                                if (pag != null && pag.getPixCopiaECola() != null) {
                                    // Salvar dados do agendamento no pagamento
                                    pag.setDataAgendamento(data.toString());
                                    pag.setHoraAgendamento(horaEscolhida);
                                    pag.setServicoNome(servicoNome);
                                    pagamentoRepository.save(pag);

                                    // ✅ ENVIAR PIX PARA O CLIENTE
                                    DateTimeFormatter parser = DateTimeFormatter.ofPattern("HH:mm");
                                    LocalTime inicioConf = LocalTime.parse(horaEscolhida, parser);
                                    LocalTime fimConf = inicioConf.plusMinutes(duracao);
                                    String intervaloHorario = inicioConf.format(parser) + " às " + fimConf.format(parser);

                                    DecimalFormat df = new DecimalFormat("#,##0.00",
                                            new DecimalFormatSymbols(new Locale("pt", "BR")));

                                    String msgPagamento =
                                            "💰 *Pagamento Antecipado*\n\n" +
                                                    "📅 Data: " + data.format(DateTimeFormatter.ofPattern("dd/MM")) + "\n" +
                                                    "🕐 Horário: " + intervaloHorario + "\n" +
                                                    "📋 Serviço: " + servicoNome + "\n" +
                                                    "💵 Valor: R$ " + df.format(valor) + "\n\n" +
                                                    "🔐 *Pague via PIX para confirmar:*\n" +
                                                    "📋 Copie o código abaixo:\n" +
                                                    "`" + pag.getPixCopiaECola() + "`\n\n" +
                                                    "⏰ Expira em 30 minutos.\n" +
                                                    "Após o pagamento, seu agendamento será confirmado automaticamente!";

                                    metaService.enviarMensagem(de, msgPagamento, clinica);

                                    // ✅ Mudar estado para aguardar pagamento
                                    estado.setEstadoAtual(12); // Novo estado: aguardando pagamento
                                    estado.getDadosTemporarios().put("pagamento_id", pag.getId().toString());
                                    estadoRepository.save(estado);
                                    return;
                                } else {
                                    metaService.enviarMensagem(de,
                                            "❌ Erro ao gerar pagamento. Entre em contato com " +
                                                    clinica.getNome(), clinica);
                                    return;
                                }
                            } else {
                                String descricaoEvento = "Telefone: " + de + " | " + servicoNome;
                                DateTimeFormatter parser = DateTimeFormatter.ofPattern("HH:mm");
                                LocalTime inicioConf = LocalTime.parse(horaEscolhida, parser);
                                LocalTime fimConf = inicioConf.plusMinutes(duracao);
                                String intervaloHorario = inicioConf.format(parser) + " às " + fimConf.format(parser);
                                String calendarIdFinal = resolverCalendarId(estado, clinica);

                                eventService.criarAgendamento(tituloEvento, data, de, horaEscolhida, calendarIdFinal, duracao, descricaoEvento);

                                metaService.enviarMensagem(de, "✅ *Seu agendamento foi realizado.*\n\n" +
                                        "📅 *Data:* " + data.format(DateTimeFormatter.ofPattern("dd/MM")) + "\n" +
                                        "🕒 *Horário:* " + intervaloHorario + "\n" +
                                        "📝 *Serviço:* " + servicoNome + "\n" +
                                        "📍 *Local:* " + clinica.getEndereco() + "\n\n" +
                                        "Tamo junto! Nos vemos em breve 👊🔥 \n" +
                                        "Caso precise, digite *menu* para outras opções.", clinica);
                                estado.setEstadoAtual(0);
                                estado.setPaginaHorarios(0);
                                estado.setHorariosTemporarios(null);
                                estado.setDataSugerida(null);
                                estado.setServicoSelecionado(null);
                                estado.setProfissionalSelecionado(null);
                                estado.getDadosTemporarios().clear();
                                estadoRepository.save(estado);
                                return;
                            }
                        } else {
                            metaService.enviarMensagem(de, "Número inválido. Digite um número da lista acima ou *+* para mais horários.", clinica);
                            return;
                            }
                        }
                        try {
                            interpretarData(msgLimpa);
                            estado.setEstadoAtual(3);
                            estado.setPaginaHorarios(0);
                            estadoRepository.save(estado);
                            executarLogicaCoringaAgenda(de, msgLimpa, clinica, estado);

                        } catch (IllegalArgumentException e) {
                            metaService.enviarMensagem(de, "⚠️ Não entendi. Digite o *número* do horário ou uma *nova data* (ex: Amanhã, 22/08).", clinica);
                        }
                    } catch (Exception e) {
                        logger.error("Erro no Case 4: ", e);
                        metaService.enviarMensagem(de, "Desculpe, ocorreu um erro. Digite *menu* para recomeçar.", clinica);
                    }
                    break;

                case 6:
                    String eventId = estado.getDadosTemporarios().get("cancel_id_" + msgLimpa);
                    if (eventId != null) {
                        metaService.enviarMensagem(de, "O que deseja fazer com esse agendamento?\n1. ❌ Cancelar apenas\n2. 🔄 Remarcar (Mudar data)\n3. ⏪ Voltar", clinica);
                        estado.getDadosTemporarios().put("_selected_event", eventId);
                        estado.setEstadoAtual(7);
                        estadoRepository.save(estado);
                    } else {
                        metaService.enviarMensagem(de, "Número inválido. Digite o número da lista acima. Ou *menu*", clinica);
                    }
                    break;

                case 7:
                    String idEvento = estado.getDadosTemporarios().get("_selected_event");
                    if (msgLimpa.equals("1")) {
                        Event event = eventService.buscarEventoSeguro(idEvento, calendarId, de);

                        if (event == null) {
                            metaService.enviarMensagem(de,
                                    "❌ Evento não encontrado ou não pertence a você. Digite *menu*.", clinica);
                            estado.setEstadoAtual(0);
                            estadoRepository.save(estado);
                            break;
                        }

                        boolean temPagamento = event.getDescription() != null &&
                                event.getDescription().contains("PAGO");

                        if (temPagamento) {
                            // Calcular horas até o evento
                            String inicioStr = event.getStart().getDateTime().toString();
                            LocalDateTime inicioEvento = LocalDateTime.parse(
                                    inicioStr.substring(0, 19)
                            );
                            long horasAte = ChronoUnit.HOURS.between(LocalDateTime.now(), inicioEvento);
                            String msgCancelamento = "";

                            if (horasAte >= 24) {
                                // ✅ REEMBOLSAR 100%
                                // Buscar pagamento no banco
                                // (Você precisa adicionar eventId no Pagamento pra facilitar)
                                Pagamento pag = pagamentoRepository
                                        .findByTelefoneClienteAndDataAgendamento(de,
                                                inicioEvento.toLocalDate().toString())
                                        .orElse(null);

                                if (pag != null && "approved".equals(pag.getStatus())) {
                                    boolean reembolsado = pagamentoService.reembolsarPagamento(
                                            pag.getMercadoPagoId(), 1.0  // 100%
                                    );

                                    if (reembolsado) {
                                        msgCancelamento = "✅ Agendamento cancelado.\n" +
                                                "💰 Reembolso de 100% será processado em até 3 dias úteis.";
                                        pag.setStatus("refunded");
                                        pagamentoRepository.save(pag);
                                    } else {
                                        msgCancelamento = "✅ Agendamento cancelado.\n" +
                                                "⚠️ Erro ao processar reembolso. Entre em contato: Digite *menu* e depois 4 para falar com um atendente";
                                    }
                                } else {
                                    msgCancelamento = "✅ Agendamento cancelado.\n" +
                                            "Entre em contato para reembolso: Digite *menu* e depois 4 para falar com um atendente";
                                }

                            } else if (horasAte >= 2) {
                                // ✅ REEMBOLSAR 50%
                                Pagamento pag = pagamentoRepository
                                        .findByTelefoneClienteAndDataAgendamento(de,
                                                inicioEvento.toLocalDate().toString())
                                        .orElse(null);

                                if (pag != null) {
                                    boolean reembolsado = pagamentoService.reembolsarPagamento(
                                            pag.getMercadoPagoId(), 0.5  // 50%
                                    );

                                    if (reembolsado) {
                                        msgCancelamento = "⚠️ Agendamento cancelado.\n" +
                                                "💰 Reembolso de 50% será processado em até 3 dias úteis.\n" +
                                                "(Política: cancelamento com menos de 24h)";
                                        pag.setStatus("partially_refunded");
                                        pagamentoRepository.save(pag);
                                    } else {
                                        msgCancelamento = "Erro ao processar reembolso. Contate: Digite *menu* e depois 4 para falar com um atendente";
                                    }
                                } else {
                                    msgCancelamento = "Cancelamento registrado. Contate para reembolso: Digite *menu* e depois 4 para falar com um atendente";
                                }

                            } else {
                                msgCancelamento = "❌ Não é possível cancelar com menos de 2h de antecedência.\n" +
                                        "O profissional já está preparado para te atender.\n" +
                                        "Entre em contato: Digite *menu* e depois 4 para falar com um atendente";
                            }
                            eventService.cancelarEvento(idEvento, calendarId);
                            metaService.enviarMensagem(de, msgCancelamento, clinica);
                        } else {
                            // Sem pagamento → cancelamento simples
                            eventService.cancelarEvento(idEvento, calendarId);
                            metaService.enviarMensagem(de,
                                    "✅ Agendamento cancelado com sucesso!\n Caso precise, digite *menu* para mais opções", clinica);
                        }
                        estado.setEstadoAtual(0);
                        estadoRepository.save(estado);
                    } else if (msgLimpa.equals("2")) {
                        eventService.cancelarEvento(idEvento, calendarId);
                        metaService.enviarMensagem(de, "✅ Agendamento anterior removido.\nAgora, digite a *NOVA* data desejada (Ex: *20/01* ou *Terça*):", clinica);
                        estado.setEstadoAtual(3);
                        estadoRepository.save(estado);
                    } else {
                        metaService.enviarMensagem(de, "Operação cancelada. Voltando ao menu.\n\n1. ✅ Novo Agendamento\n2. ❌ Cancelar/Remarcar\n3. 📞 Informações e preços\n4. 💬 Falar com atendente", clinica);
                        estado.setEstadoAtual(2);
                        estadoRepository.save(estado);
                    }
                    break;

                case 10: // Processar a escolha do serviço
                    try {
                        List<TipoServico> servicos = servicoRepository.findByClinicaId(clinica.getId());
                        int idx = Integer.parseInt(msgLimpa.replaceAll("\\D", "")) - 1;
                        if (idx >= 0 && idx < servicos.size()) {
                            estado.setServicoSelecionado(servicos.get(idx));
                            List<ServicoProfissional> profissionaisServico =
                                    servicoProfissionalRepository.findProfissionaisByServico(servicos.get(idx).getId());
                            if (profissionaisServico.isEmpty()) {
                            metaService.enviarMensagem(de, "Você escolheu: *" + servicos.get(idx).getNome() + "*.\nAgora, digite a data desejada (Ex: *20/01* ou *Terça*):", clinica);
                            estado.setEstadoAtual(3);
                            estadoRepository.save(estado);
                            } else if (profissionaisServico.size() == 1) {
                                // Só 1 profissional → seleciona automaticamente
                                ServicoProfissional sp = profissionaisServico.get(0);
                                estado.setProfissionalSelecionado(sp.getProfissional());
                                BigDecimal precoFinal = (sp.getPrecoCustomizado() != null) ?
                                        (sp.getPrecoCustomizado()) : servicos.get(idx).getPreco();
                                DecimalFormat df = new DecimalFormat("#,##0.00",
                                        new DecimalFormatSymbols(new Locale("pt", "BR")));
                                metaService.enviarMensagem(de,
                                        "Você escolheu: *" + servicos.get(idx).getNome() + "* com *" +
                                                sp.getProfissional().getNome() + "* por *R$ " + df.format(precoFinal) + "*.\n\n" +
                                                "Agora, digite a data desejada (Ex: *20/01* ou *Sexta*):", clinica);
                                estado.setEstadoAtual(3);
                                estadoRepository.save(estado);
                            } else {
                                // Múltiplos profissionais → mostra lista
                                StringBuilder sb = new StringBuilder("🧙‍♂️ *Escolha o profissional:*\n\n");

                                DecimalFormat df = new DecimalFormat("#,##0.00",
                                        new DecimalFormatSymbols(new Locale("pt", "BR")));

                                for (int i = 0; i < profissionaisServico.size(); i++) {
                                    ServicoProfissional sp = profissionaisServico.get(i);
                                    BigDecimal precoFinal = (sp.getPrecoCustomizado() != null) ?
                                            (sp.getPrecoCustomizado()) : servicos.get(idx).getPreco();

                                    sb.append(i + 1).append(". *")
                                            .append(sp.getProfissional().getNome())
                                            .append("* - R$ ")
                                            .append(df.format(precoFinal)).append("\n");
                                }

                                sb.append("\nDigite o *número* da sua escolha:");
                                metaService.enviarMensagem(de, sb.toString(), clinica);
                                estado.setEstadoAtual(11);

                                // Salvar lista temporária
                                estado.getDadosTemporarios().put("profissionais_disponiveis",
                                        profissionaisServico.stream()
                                                .map(sp -> sp.getId().toString())
                                                .collect(Collectors.joining(",")));
                            }
                            estadoRepository.save(estado);
                            } else {
                            metaService.enviarMensagem(de, "Número inválido. Tente novamente e caso precise digite *menu*.", clinica);
                        }
                    } catch (NumberFormatException e) {
                        metaService.enviarMensagem(de, "Digite apenas o número do serviço.", clinica);
                    }
                    break;

                case 11: // Escolher profissional
                    try {
                        String idsDisponiveis = estado.getDadosTemporarios().get("profissionais_disponiveis");
                        if (idsDisponiveis == null) {
                            metaService.enviarMensagem(de, "Sessão expirada. Digite *menu*.", clinica);
                            estado.setEstadoAtual(0);
                            estadoRepository.save(estado);
                            break;
                        }
                        String[] ids = idsDisponiveis.split(",");
                        int idx = Integer.parseInt(msgLimpa.replaceAll("\\D", "")) - 1;
                        if (idx >= 0 && idx < ids.length) {
                            Long servicoProfissionalId = Long.parseLong(ids[idx]);
                            ServicoProfissional sp = servicoProfissionalRepository
                                    .findById(servicoProfissionalId).orElse(null);
                            if (sp != null) {
                                estado.setProfissionalSelecionado(sp.getProfissional());
                                // Atualizar preço e duração customizados se houver
                                if (sp.getPrecoCustomizado() != null) {
                                    estado.getServicoSelecionado().setPreco((sp.getPrecoCustomizado()));
                                }
                                if (sp.getDuracaoCustomizada() != null) {
                                    estado.getServicoSelecionado().setDuracaoMinutos(sp.getDuracaoCustomizada());
                                }
                                metaService.enviarMensagem(de,
                                        "Ótimo! Agendando com *" + sp.getProfissional().getNome() + "* 👊\n\n" +
                                                "Agora digite a data desejada (ex: *22/08* ou *Sexta*):", clinica);
                                estado.setEstadoAtual(3);
                                estadoRepository.save(estado);
                            } else {
                                metaService.enviarMensagem(de, "Erro ao selecionar, tente novamente. OU digite *menu*.", clinica);
                            }
                        } else {
                            metaService.enviarMensagem(de,
                                    "Número inválido. Escolha um da lista ou digite *menu*.", clinica);
                        }
                    } catch (NumberFormatException e) {
                        metaService.enviarMensagem(de, "Digite apenas o número do profissional.", clinica);
                    }
                    break;

                case 12: // Aguardando confirmação de pagamento
                    String pagamentoIdStr = estado.getDadosTemporarios().get("pagamento_id");
                    if (pagamentoIdStr == null) {
                        metaService.enviarMensagem(de, "Sessão expirada. Digite *menu* para recomeçar.", clinica);
                        estado.setEstadoAtual(0);
                        estadoRepository.save(estado);
                        break;
                    }

                    // Opção de cancelar
                    if (msgLimpa.contains("cancelar")) {
                        metaService.enviarMensagem(de,
                                "Agendamento cancelado. O PIX expira automaticamente em 30 minutos.\n" +
                                        "Digite *menu* para outras opções.", clinica);
                        estado.setEstadoAtual(0);
                        estado.getDadosTemporarios().clear();
                        estado.setPaginaHorarios(0);
                        estado.setHorariosTemporarios(null);
                        estado.setDataSugerida(null);
                        estado.setServicoSelecionado(null);
                        estado.setProfissionalSelecionado(null);
                        estadoRepository.save(estado);
                        break;
                    }

                    Long pagamentoId = Long.parseLong(pagamentoIdStr);
                    Pagamento pag = pagamentoRepository.findById(pagamentoId).orElse(null);

                    if (pag == null) {
                        metaService.enviarMensagem(de, "Pagamento não encontrado. Digite *menu*.", clinica);
                        estado.setEstadoAtual(0);
                        estadoRepository.save(estado);
                        break;
                    }

                    // ✅ Verificar status
                    String status = pagamentoService.consultarStatus(pag.getMercadoPagoId());

                    if ("approved".equals(status)) {
                        // ✅ CRIAR AGENDAMENTO
                        LocalDate data = LocalDate.parse(pag.getDataAgendamento());
                        String horaEscolhida = pag.getHoraAgendamento();
                        int duracao = (estado.getServicoSelecionado() != null) ?
                                estado.getServicoSelecionado().getDuracaoMinutos() : 30;

                        String tituloEvento = estado.getNomePaciente();
                        String descricaoEvento = "Telefone: " + de + " | " + pag.getServicoNome() + " | PAGO";

                        String calendarIdFinal = resolverCalendarId(estado, clinica);

                        try {
                            eventService.criarAgendamento(tituloEvento, data, de, horaEscolhida,
                                    calendarIdFinal, duracao, descricaoEvento);

                            DateTimeFormatter parser = DateTimeFormatter.ofPattern("HH:mm");
                            LocalTime inicioConf = LocalTime.parse(horaEscolhida, parser);
                            LocalTime fimConf = inicioConf.plusMinutes(duracao);
                            String intervaloHorario = inicioConf.format(parser) + " às " + fimConf.format(parser);

                            metaService.enviarMensagem(de,
                                    "✅ *Pagamento Confirmado!*\n\n" +
                                            "Seu agendamento foi confirmado:\n\n" +
                                            "📅 *Data:* " + data.format(DateTimeFormatter.ofPattern("dd/MM")) + "\n" +
                                            "🕒 *Horário:* " + intervaloHorario + "\n" +
                                            "📝 *Serviço:* " + pag.getServicoNome() + "\n" +
                                            "📍 *Local:* " + clinica.getEndereco() + "\n\n" +
                                            "Tamo junto! Nos vemos em breve 👊🔥", clinica);

                            // Resetar estado
                            estado.setEstadoAtual(0);
                            estado.setPaginaHorarios(0);
                            estado.setHorariosTemporarios(null);
                            estado.setDataSugerida(null);
                            estado.setServicoSelecionado(null);
                            estado.setProfissionalSelecionado(null);
                            estado.getDadosTemporarios().clear();
                            estadoRepository.save(estado);

                        } catch (IOException e) {
                            logger.error("Erro ao criar agendamento após pagamento: ", e);
                            metaService.enviarMensagem(de,
                                    "Pagamento confirmado mas erro ao criar agendamento. " +
                                            "Entre em contato: Digite *menu* e depois 4.", clinica);
                        }

                    } else if ("pending".equals(status)) {
                        metaService.enviarMensagem(de,
                                "⏳ *Aguardando pagamento...*\n\n" +
                                        "Assim que o PIX for compensado, você receberá a confirmação automática.\n\n" +
                                        "💡 Não precisa fazer nada! O sistema confirma sozinho.\n\n" +
                                        "Caso queira cancelar, digite *cancelar*.", clinica);
                    } else {
                        metaService.enviarMensagem(de,
                                "❌ Pagamento não confirmado.\n" +
                                        "Status: " + status + "\n\n" +
                                        "Digite *menu* ou entre em contato.", clinica);
                    }
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            metaService.enviarMensagem(de, "Ops! Algo deu errado por aqui. 😕\n" +
                    "Pode tentar enviar novamente ou digitar *menu* para recomeçar?", clinica);
        }

        estadoRepository.save(estado);
    }

    private String gerarTextoHorario(Clinica clinica) {
        StringBuilder sb = new StringBuilder();
        String[] nomesDias = {"", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"};

        // 1. Lógica Seg-Sex
        Set<Integer> folgas = extrairFolgas(clinica.getDiasFolga());
        List<String> uteisAtende = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            if (!folgas.contains(i)) uteisAtende.add(nomesDias[i]);
        }

        if (uteisAtende.size() == 5) {
            sb.append("• Seg a Sex: ").append(clinica.getHorarioAbertura()).append("h às ").append(clinica.getHorarioFechamento()).append("h\n");
        } else {
            uteisAtende.forEach(dia -> sb.append("• ").append(dia).append(": ").append(clinica.getHorarioAbertura()).append("h às ").append(clinica.getHorarioFechamento()).append("h\n"));
        }

        // 2. Sábado (Tratando horário diferenciado se existir)
        if (clinica.isTrabalhaSabado()) {
            int inicio = (clinica.getAberturaSabado() != null) ? clinica.getAberturaSabado() : clinica.getHorarioAbertura();
            int fim = (clinica.getFechamentoSabado() != null) ? clinica.getFechamentoSabado() : clinica.getHorarioFechamento();
            sb.append("• Sábado: ").append(inicio).append("h às ").append(fim).append("h\n");
        }

        // 3. Domingo
        if (clinica.isTrabalhaDomingo()) {
            sb.append("• Domingo: ").append(clinica.getHorarioAbertura()).append("h às ").append(clinica.getHorarioFechamento()).append("h\n");
        }

        if (clinica.getHoraAlmocoInicio() != null) {
            sb.append("\n_Pausa para almoço: ").append(clinica.getHoraAlmocoInicio()).append("h às ").append(clinica.getHoraAlmocoFim()).append("h_");
        }

        return sb.toString();
    }

    private Set<Integer> extrairFolgas(String diasFolgaStr) {
        if (diasFolgaStr == null || diasFolgaStr.isEmpty()) return Collections.emptySet();
        return Arrays.stream(diasFolgaStr.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
    }

    private String formatarNome(String nome) {
        if (nome == null || nome.isEmpty()) return "";
        String[] palavras = nome.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : palavras) {
            if (p.length() > 0) {
                sb.append(Character.toUpperCase(p.charAt(0)))
                        .append(p.substring(1).toLowerCase()).append(" ");
            }
        }
        return sb.toString().trim();
    }
    private String resolverCalendarId(EstadoConversa estado, Clinica clinica) {
        if (estado.getProfissionalSelecionado() != null
                && estado.getProfissionalSelecionado().getGoogleCalendarId() != null) {
            return estado.getProfissionalSelecionado().getGoogleCalendarId();
        }
        return clinica.getGoogleCalendarId();
    }

    private LocalDate interpretarData(String texto) {
        if (texto.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(texto);
        }
        // Remove "-feira" para garantir que "segunda-feira" vire "segunda"
        String msg = texto.toLowerCase().trim().replace("-feira", "");
        LocalDate hoje = LocalDate.now(ZONE);

        if (msg.contains("hoje")) return hoje;
        if (msg.contains("amanhã") || msg.contains("amanha") || msg.contains("amnh")) return hoje.plusDays(1);

        Map<String, DayOfWeek> dias = Map.of(
                "segunda", DayOfWeek.MONDAY, "terca", DayOfWeek.TUESDAY, "terça", DayOfWeek.TUESDAY,
                "quarta", DayOfWeek.WEDNESDAY, "quinta", DayOfWeek.THURSDAY,
                "sexta", DayOfWeek.FRIDAY, "sabado", DayOfWeek.SATURDAY, "sábado", DayOfWeek.SATURDAY, "domingo", DayOfWeek.SUNDAY
        );

        for (Map.Entry<String, DayOfWeek> entry : dias.entrySet()) {
            if (msg.contains(entry.getKey())) {
                LocalDate data = hoje;
                do {
                    data = data.plusDays(1);
                } while (data.getDayOfWeek() != entry.getValue());
                return data;
            }
        }

        if (msg.matches(".*\\d{1,2}/\\d{1,2}.*")) {
            String[] partes = msg.split("/");
            // Remove qualquer coisa que não seja numero
            int dia = Integer.parseInt(partes[0].replaceAll("\\D", ""));
            int mes = Integer.parseInt(partes[1].replaceAll("\\D", ""));
            return LocalDate.of(hoje.getYear(), mes, dia);
        }
        throw new IllegalArgumentException("Data não compreendida");
    }
}