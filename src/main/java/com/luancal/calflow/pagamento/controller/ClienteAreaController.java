package com.luancal.calflow.pagamento.controller;

import com.luancal.calflow.model.*;
import com.luancal.calflow.pagamento.domain.ClienteCalFlow;
import com.luancal.calflow.pagamento.repository.ClienteCalFlowRepository;
import com.luancal.calflow.pagamento.service.EvolutionServiceCF;
import com.luancal.calflow.pagamento.service.JwtService;
import com.luancal.calflow.repository.*;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.math.BigDecimal;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/cliente")
@RequiredArgsConstructor
public class ClienteAreaController {

    private final JwtService jwtService;
    private final ClienteCalFlowRepository clienteRepo;
    private final ClinicaRepository clinicaRepo;
    private final TipoServicoRepository servicoRepo;
    private final ProfissionalRepository profissionalRepo;
    private final ServicoProfissionalRepository servicoProfRepo;

    private final EvolutionServiceCF evolutionService;

    // ============================================================
    // HELPER: extrair cliente do token
    // ============================================================
    private ClienteCalFlow getClienteFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Token inválido");
        }
        String token = authHeader.substring(7);
        Claims claims = jwtService.validarToken(token);
        String clienteId = claims.getSubject();
        return clienteRepo.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
    }

    private Clinica getClinica(ClienteCalFlow cliente) {
        if (cliente.getClinicaId() == null) {
            throw new RuntimeException("Clinica não configurada para este cliente");
        }
        return clinicaRepo.findById(cliente.getClinicaId())
                .orElseThrow(() -> new RuntimeException("Clinica não encontrada"));
    }

    // ============================================================
    // ME (dados do usuário logado)
    // ============================================================
    @GetMapping("/me")
    public ResponseEntity<?> getMe(@RequestHeader("Authorization") String authHeader) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);

            Map<String, Object> response = new HashMap<>();
            response.put("id", cliente.getId());
            response.put("usuario", cliente.getUsuario());
            response.put("nome", cliente.getNome());
            response.put("email", cliente.getEmail());
            response.put("negocio", cliente.getNomeNegocio() != null ? cliente.getNomeNegocio() : "");
            response.put("tipo", cliente.getTipo());
            response.put("plano", cliente.getPlano() != null ? cliente.getPlano() : "mensal");
            response.put("status", cliente.getStatus());
            response.put("dataVencimento", cliente.getDataVencimento() != null ? cliente.getDataVencimento().toString() : "");
            response.put("nomeInstancia", cliente.getNomeInstancia() != null ? cliente.getNomeInstancia() : "");
            response.put("clinicaId", cliente.getClinicaId() != null ? cliente.getClinicaId() : "");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    // ============================================================
    // DASHBOARD
    // ============================================================
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(@RequestHeader("Authorization") String authHeader) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);
            // Por enquanto retorna métricas básicas
            // Futuramente: buscar do Google Calendar via EventService
            return ResponseEntity.ok(Map.of(
                    "agendamentosHoje", 0,
                    "confirmados", 0,
                    "faltas", 0,
                    "receitaHoje", 0,
                    "agendamentos", List.of()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    // ============================================================
    // CONFIGURAÇÕES
    // ============================================================
    @GetMapping("/configuracoes")
    public ResponseEntity<?> getConfiguracoes(@RequestHeader("Authorization") String authHeader) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);
            Map<String, Object> config = new HashMap<>();
            config.put("nomeNegocio", cliente.getNomeNegocio() != null ? cliente.getNomeNegocio() : "");

            if (cliente.getClinicaId() != null) {
                clinicaRepo.findById(cliente.getClinicaId()).ifPresent(clinica -> {
                    config.put("endereco", clinica.getEndereco() != null ? clinica.getEndereco() : "");
                    config.put("whatsapp", clinica.getTelefoneDono() != null ? clinica.getTelefoneDono() : "");
                    config.put("googleCalendarId", clinica.getGoogleCalendarId() != null ? clinica.getGoogleCalendarId() : "");
                    config.put("intervalo", clinica.getIntervaloPadrao());
                    config.put("mercadoPagoToken", clinica.getMercadoPagoToken() != null ? clinica.getMercadoPagoToken() : "");
                    config.put("chavePix", clinica.getChavePix() != null ? clinica.getChavePix() : "");
                    config.put("nomePix", clinica.getNomePix() != null ? clinica.getNomePix() : "");
                    config.put("cobrancaAntecipada", clinica.getCobrancaAntecipada() != null ? clinica.getCobrancaAntecipada() : false);
                    config.put("botAtivo", clinica.isBotAtivo());
                    config.put("lembreteAtivo", clinica.isLembreteAtivo());
                    config.put("mensagemLembrete", clinica.getMensagemLembrete() != null ? clinica.getMensagemLembrete() : "");
                    config.put("saudacaoBot", clinica.getSaudacaoBot() != null ? clinica.getSaudacaoBot() : "Olá! Como posso ajudar?");
                    config.put("mensagemEncerramento", clinica.getMensagemEncerramento() != null ? clinica.getMensagemEncerramento() : "Obrigado pelo contato!");
                    config.put("confirmarManual", clinica.getConfirmarManual() != null ? clinica.getConfirmarManual() : false);
                    config.put("antecedenciaMinimaHoras", clinica.getAntecedenciaMinimaHoras() != null ? clinica.getAntecedenciaMinimaHoras() : 2);

                    // Horários
                    Map<String, Object> horarios = new HashMap<>();
                    int abertura = clinica.getHorarioAbertura() != null ? clinica.getHorarioAbertura() : 9;
                    int fechamento = clinica.getHorarioFechamento() != null ? clinica.getHorarioFechamento() : 18;
                    String aberturaStr = String.format("%02d:00", abertura);
                    String fechamentoStr = String.format("%02d:00", fechamento);

                    Set<Integer> folgas = new HashSet<>();
                    if (clinica.getDiasFolga() != null && !clinica.getDiasFolga().isEmpty()) {
                        for (String d : clinica.getDiasFolga().split(",")) {
                            folgas.add(Integer.parseInt(d.trim()));
                        }
                    }

                    String[] dias = {"segunda", "terca", "quarta", "quinta", "sexta"};
                    int[] diaNum = {1, 2, 3, 4, 5};
                    for (int i = 0; i < dias.length; i++) {
                        Map<String, Object> dia = new HashMap<>();
                        dia.put("ativo", !folgas.contains(diaNum[i]));
                        dia.put("abertura", aberturaStr);
                        dia.put("fechamento", fechamentoStr);
                        horarios.put(dias[i], dia);
                    }

                    // Sábado
                    Map<String, Object> sabado = new HashMap<>();
                    sabado.put("ativo", clinica.isTrabalhaSabado());
                    int abSab = clinica.getAberturaSabado() != null ? clinica.getAberturaSabado() : abertura;
                    int feSab = clinica.getFechamentoSabado() != null ? clinica.getFechamentoSabado() : 14;
                    sabado.put("abertura", String.format("%02d:00", abSab));
                    sabado.put("fechamento", String.format("%02d:00", feSab));
                    horarios.put("sabado", sabado);

                    // Domingo
                    Map<String, Object> domingo = new HashMap<>();
                    domingo.put("ativo", clinica.isTrabalhaDomingo());
                    domingo.put("abertura", aberturaStr);
                    domingo.put("fechamento", String.format("%02d:00", 13));
                    horarios.put("domingo", domingo);

                    // Almoço
                    Map<String, Object> almoco = new HashMap<>();
                    almoco.put("ativo", clinica.getHoraAlmocoInicio() != null);
                    almoco.put("inicio", clinica.getHoraAlmocoInicio() != null ? String.format("%02d:00", clinica.getHoraAlmocoInicio()) : "12:00");
                    almoco.put("fim", clinica.getHoraAlmocoFim() != null ? String.format("%02d:00", clinica.getHoraAlmocoFim()) : "13:00");
                    horarios.put("almoco", almoco);

                    config.put("horarios", horarios);
                });
            } else {
                config.put("endereco", "");
                config.put("whatsapp", "");
                config.put("googleCalendarId", "");
                config.put("intervalo", 30);
                config.put("mercadoPagoToken", "");
                config.put("chavePix", "");
                config.put("nomePix", "");
                config.put("cobrancaAntecipada", false);
                config.put("botAtivo", true);
                config.put("lembreteAtivo", true);
                config.put("saudacaoBot", "Olá! Como posso ajudar?");
                config.put("mensagemEncerramento", "Obrigado pelo contato!");
                config.put("confirmarManual", false);
                config.put("antecedenciaMinimaHoras", 2);
            }

            return ResponseEntity.ok(config);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(401).body(error);
        }
    }

    @PutMapping("/configuracoes")
    public ResponseEntity<?> salvarConfiguracoes(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);

            if (body.containsKey("nomeNegocio")) {
                cliente.setNomeNegocio((String) body.get("nomeNegocio"));
                clienteRepo.save(cliente);
            }

            Clinica clinica;
            if (cliente.getClinicaId() == null) {
                clinica = new Clinica();
                clinica.setNomeInstancia(cliente.getNomeInstancia());
            } else {
                clinica = clinicaRepo.findById(cliente.getClinicaId()).orElse(new Clinica());
            }

            // Dados básicos
            if (body.containsKey("nomeNegocio")) clinica.setNome((String) body.get("nomeNegocio"));
            if (body.containsKey("endereco")) clinica.setEndereco((String) body.get("endereco"));
            if (body.containsKey("whatsapp")) clinica.setTelefoneDono((String) body.get("whatsapp"));
            if (body.containsKey("googleCalendarId")) clinica.setGoogleCalendarId((String) body.get("googleCalendarId"));
            if (body.containsKey("intervalo")) clinica.setIntervaloPadrao(Integer.parseInt(body.get("intervalo").toString()));

            // Pagamentos
            if (body.containsKey("mercadoPagoToken")) clinica.setMercadoPagoToken((String) body.get("mercadoPagoToken"));
            if (body.containsKey("chavePix")) clinica.setChavePix((String) body.get("chavePix"));
            if (body.containsKey("nomePix")) clinica.setNomePix((String) body.get("nomePix"));
            if (body.containsKey("cobrancaAntecipada")) clinica.setCobrancaAntecipada(Boolean.parseBoolean(body.get("cobrancaAntecipada").toString()));

            // Bot
            if (body.containsKey("botAtivo")) clinica.setBotAtivo(Boolean.parseBoolean(body.get("botAtivo").toString()));
            if (body.containsKey("lembreteAtivo")) clinica.setLembreteAtivo(Boolean.parseBoolean(body.get("lembreteAtivo").toString()));
            if (body.containsKey("mensagemLembrete")) clinica.setMensagemLembrete((String) body.get("mensagemLembrete"));
            if (body.containsKey("saudacaoBot")) clinica.setSaudacaoBot((String) body.get("saudacaoBot"));
            if (body.containsKey("mensagemEncerramento")) clinica.setMensagemEncerramento((String) body.get("mensagemEncerramento"));
            if (body.containsKey("confirmarManual")) clinica.setConfirmarManual(Boolean.parseBoolean(body.get("confirmarManual").toString()));
            if (body.containsKey("antecedenciaMinimaHoras")) clinica.setAntecedenciaMinimaHoras(Integer.parseInt(body.get("antecedenciaMinimaHoras").toString()));

            // Horários
            if (body.containsKey("horarios")) {
                Map<String, Object> horarios = (Map<String, Object>) body.get("horarios");

                if (horarios.containsKey("segunda")) {
                    Map<String, Object> seg = (Map<String, Object>) horarios.get("segunda");
                    // Os horários de abertura/fechamento da clínica são valores simples
                    // Usamos os da segunda como padrão (simplificação)
                    if (seg.containsKey("abertura")) {
                        String abertura = (String) seg.get("abertura");
                        clinica.setHorarioAbertura(Integer.parseInt(abertura.split(":")[0]));
                    }
                    if (seg.containsKey("fechamento")) {
                        String fechamento = (String) seg.get("fechamento");
                        clinica.setHorarioFechamento(Integer.parseInt(fechamento.split(":")[0]));
                    }
                }

                if (horarios.containsKey("sabado")) {
                    Map<String, Object> sab = (Map<String, Object>) horarios.get("sabado");
                    clinica.setTrabalhaSabado(Boolean.parseBoolean(sab.get("ativo").toString()));
                    if (sab.containsKey("abertura")) {
                        clinica.setAberturaSabado(Integer.parseInt(((String) sab.get("abertura")).split(":")[0]));
                    }
                    if (sab.containsKey("fechamento")) {
                        clinica.setFechamentoSabado(Integer.parseInt(((String) sab.get("fechamento")).split(":")[0]));
                    }
                }

                if (horarios.containsKey("domingo")) {
                    Map<String, Object> dom = (Map<String, Object>) horarios.get("domingo");
                    clinica.setTrabalhaDomingo(Boolean.parseBoolean(dom.get("ativo").toString()));
                }

                if (horarios.containsKey("almoco")) {
                    Map<String, Object> almoco = (Map<String, Object>) horarios.get("almoco");
                    if (Boolean.parseBoolean(almoco.get("ativo").toString())) {
                        clinica.setHoraAlmocoInicio(Integer.parseInt(((String) almoco.get("inicio")).split(":")[0]));
                        clinica.setHoraAlmocoFim(Integer.parseInt(((String) almoco.get("fim")).split(":")[0]));
                    } else {
                        clinica.setHoraAlmocoInicio(null);
                        clinica.setHoraAlmocoFim(null);
                    }
                }

                // Dias de folga
                StringBuilder diasFolga = new StringBuilder();
                Map<String, Integer> diaMap = Map.of(
                        "segunda", 1, "terca", 2, "quarta", 3,
                        "quinta", 4, "sexta", 5
                );
                for (Map.Entry<String, Integer> entry : diaMap.entrySet()) {
                    if (horarios.containsKey(entry.getKey())) {
                        Map<String, Object> dia = (Map<String, Object>) horarios.get(entry.getKey());
                        if (!Boolean.parseBoolean(dia.get("ativo").toString())) {
                            if (diasFolga.length() > 0) diasFolga.append(",");
                            diasFolga.append(entry.getValue());
                        }
                    }
                }
                clinica.setDiasFolga(diasFolga.toString());
            }

            clinica = clinicaRepo.save(clinica);

            if (cliente.getClinicaId() == null) {
                cliente.setClinicaId(clinica.getId());
                clienteRepo.save(cliente);
            }

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Erro ao salvar configurações", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    // ============================================================
    // SERVIÇOS
    // ============================================================
    @GetMapping("/servicos")
    public ResponseEntity<?> getServicos(@RequestHeader("Authorization") String authHeader) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);
            if (cliente.getClinicaId() == null) return ResponseEntity.ok(List.of());

            List<TipoServico> servicos = servicoRepo.findByClinicaId(cliente.getClinicaId());
            List<Map<String, Object>> resultado = servicos.stream().map(s -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", s.getId());
                m.put("nome", s.getNome());
                m.put("preco", s.getPreco());
                m.put("duracao", s.getDuracaoMinutos());
                m.put("descricao", s.getDescricao() != null ? s.getDescricao() : "");
                m.put("ativo", true);
                return m;
            }).toList();

            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/servicos")
    public ResponseEntity<?> criarServico(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);
            Clinica clinica = getClinica(cliente);

            TipoServico servico = new TipoServico();
            servico.setNome((String) body.get("nome"));
            servico.setPreco(new BigDecimal(body.get("preco").toString()));
            servico.setDuracaoMinutos(Integer.parseInt(body.get("duracao").toString()));
            servico.setDescricao(body.containsKey("descricao") ? (String) body.get("descricao") : "");
            servico.setClinica(clinica);

            servico = servicoRepo.save(servico);
            return ResponseEntity.ok(Map.of("id", servico.getId(), "nome", servico.getNome()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/servicos/{id}")
    public ResponseEntity<?> editarServico(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);
            TipoServico servico = servicoRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Serviço não encontrado"));

            // Garante que o serviço pertence à clínica do cliente
            if (!servico.getClinica().getId().equals(cliente.getClinicaId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Sem permissão"));
            }

            if (body.containsKey("nome")) servico.setNome((String) body.get("nome"));
            if (body.containsKey("preco")) servico.setPreco(new BigDecimal(body.get("preco").toString()));
            if (body.containsKey("duracao")) servico.setDuracaoMinutos(Integer.parseInt(body.get("duracao").toString()));
            if (body.containsKey("descricao")) servico.setDescricao((String) body.get("descricao"));

            servicoRepo.save(servico);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/servicos/{id}")
    public ResponseEntity<?> deletarServico(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);
            TipoServico servico = servicoRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Serviço não encontrado"));

            if (!servico.getClinica().getId().equals(cliente.getClinicaId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Sem permissão"));
            }

            servicoRepo.delete(servico);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ============================================================
    // FUNCIONÁRIOS
    // ============================================================
    @GetMapping("/funcionarios")
    public ResponseEntity<?> getFuncionarios(@RequestHeader("Authorization") String authHeader) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);
            if (cliente.getClinicaId() == null) return ResponseEntity.ok(List.of());

            List<Profissional> profissionais = profissionalRepo.findByClinicaId(cliente.getClinicaId());
            List<Map<String, Object>> resultado = profissionais.stream().map(p -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", p.getId());
                m.put("nome", p.getNome());
                m.put("ativo", p.isAtivo());
                m.put("googleCalendarId", p.getGoogleCalendarId() != null ? p.getGoogleCalendarId() : "");
                // Buscar serviços do profissional
                List<String> servicosNomes = servicoProfRepo.findByProfissionalIdAndAtivoTrue(p.getId())
                        .stream()
                        .map(sp -> sp.getTipoServico().getNome())
                        .toList();
                m.put("servicos", servicosNomes);
                return m;
            }).toList();

            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/funcionarios")
    public ResponseEntity<?> criarFuncionario(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);
            Clinica clinica = getClinica(cliente);

            Profissional prof = new Profissional();
            prof.setNome((String) body.get("nome"));
            prof.setClinica(clinica);
            prof.setAtivo(true);
            if (body.containsKey("googleCalendarId")) {
                prof.setGoogleCalendarId((String) body.get("googleCalendarId"));
            }

            prof = profissionalRepo.save(prof);
            return ResponseEntity.ok(Map.of("id", prof.getId(), "nome", prof.getNome()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/funcionarios/{id}")
    public ResponseEntity<?> editarFuncionario(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);
            Profissional prof = profissionalRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Profissional não encontrado"));

            if (!prof.getClinica().getId().equals(cliente.getClinicaId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Sem permissão"));
            }

            if (body.containsKey("nome")) prof.setNome((String) body.get("nome"));
            if (body.containsKey("ativo")) prof.setAtivo(Boolean.parseBoolean(body.get("ativo").toString()));
            if (body.containsKey("googleCalendarId")) prof.setGoogleCalendarId((String) body.get("googleCalendarId"));

            profissionalRepo.save(prof);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ============================================================
    // WHATSAPP / QR CODE
    // ============================================================
    @GetMapping("/qrcode")
    public ResponseEntity<?> getQRCode(@RequestHeader("Authorization") String authHeader) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);
            String instanceName = cliente.getNomeInstancia();

            if (instanceName == null || instanceName.isBlank()) {
                return ResponseEntity.ok(Map.of("status", "no_instance", "qrcode", ""));
            }

            String qrBase64 = evolutionService.getQRCode(instanceName);
            String connectionState = evolutionService.getConnectionStatus(instanceName);

            return ResponseEntity.ok(Map.of(
                    "status", connectionState,
                    "qrcode", qrBase64 != null ? qrBase64 : "",
                    "instanceName", instanceName
            ));
        } catch (Exception e) {
            log.error("Erro ao buscar QR Code", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reconnect")
    public ResponseEntity<?> reconnect(@RequestHeader("Authorization") String authHeader) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);
            String instanceName = cliente.getNomeInstancia();
            if (instanceName == null) return ResponseEntity.badRequest().body(Map.of("error", "Sem instância"));

            // Busca novo QR
            String qr = evolutionService.getQRCode(instanceName);
            return ResponseEntity.ok(Map.of("qrcode", qr != null ? qr : ""));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/disconnect")
    public ResponseEntity<?> disconnect(@RequestHeader("Authorization") String authHeader) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);
            // Implementar desconexão via Evolution API
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ============================================================
    // ALTERAR SENHA
    // ============================================================
    @PutMapping("/alterar-senha")
    public ResponseEntity<?> alterarSenha(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        try {
            ClienteCalFlow cliente = getClienteFromToken(authHeader);
            String senhaAtual = body.get("senhaAtual");
            String senhaNova = body.get("senhaNova");

            if (!passwordEncoder.matches(senhaAtual, cliente.getSenhaHash())) {
                return ResponseEntity.status(400).body(Map.of("error", "Senha atual incorreta"));
            }

            cliente.setSenhaHash(passwordEncoder.encode(senhaNova));
            clienteRepo.save(cliente);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}