package com.luancal.calflow.pagamento.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clientes/{clienteId}")
public class ConfiguracoesController {

    @GetMapping("/configuracoes")
    public ResponseEntity<?> getConfiguracoes(@PathVariable String clienteId) {
        return ResponseEntity.ok(Map.of(
                "clienteId", clienteId,
                "nomeNegocio", "Meu Negócio",
                "endereco", "",
                "whatsapp", "",
                "googleCalendarId", "",
                "intervalo", 30,
                "antecedencia", 2,
                "confirmarManual", false,
                "saudacaoBot", "Olá! Bem-vindo ao CalFlow!",
                "mensagemEncerramento", "Obrigado pelo contato!"
        ));
    }

    @PutMapping("/configuracoes")
    public ResponseEntity<?> salvarConfiguracoes(
            @PathVariable String clienteId,
            @RequestBody Map<String, Object> config) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "clienteId", clienteId,
                "mensagem", "Configurações recebidas com sucesso",
                "dados", config
        ));
    }

    @GetMapping("/servicos")
    public ResponseEntity<?> getServicos(@PathVariable String clienteId) {
        return ResponseEntity.ok(List.of());
    }

    @PutMapping("/servicos")
    public ResponseEntity<?> salvarServicos(
            @PathVariable String clienteId,
            @RequestBody List<Map<String, Object>> servicos) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "clienteId", clienteId,
                "mensagem", "Serviços recebidos com sucesso",
                "total", servicos.size()
        ));
    }

    @GetMapping("/horarios")
    public ResponseEntity<?> getHorarios(@PathVariable String clienteId) {
        return ResponseEntity.ok(Map.of(
                "segunda", Map.of("ativo", true, "abertura", "09:00", "fechamento", "18:00"),
                "terca", Map.of("ativo", true, "abertura", "09:00", "fechamento", "18:00"),
                "quarta", Map.of("ativo", true, "abertura", "09:00", "fechamento", "18:00"),
                "quinta", Map.of("ativo", true, "abertura", "09:00", "fechamento", "18:00"),
                "sexta", Map.of("ativo", true, "abertura", "09:00", "fechamento", "18:00"),
                "sabado", Map.of("ativo", true, "abertura", "09:00", "fechamento", "14:00"),
                "domingo", Map.of("ativo", false, "abertura", "09:00", "fechamento", "13:00"),
                "almoco", Map.of("ativo", true, "inicio", "12:00", "fim", "13:00")
        ));
    }

    @PutMapping("/horarios")
    public ResponseEntity<?> salvarHorarios(
            @PathVariable String clienteId,
            @RequestBody Map<String, Object> horarios) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "clienteId", clienteId,
                "mensagem", "Horários recebidos com sucesso",
                "dados", horarios
        ));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(@PathVariable String clienteId) {
        return ResponseEntity.ok(Map.of(
                "agendamentosHoje", 0,
                "confirmados", 0,
                "faltas", 0,
                "receita", 0
        ));
    }

    @GetMapping("/agendamentos")
    public ResponseEntity<?> getAgendamentos(@PathVariable String clienteId) {
        return ResponseEntity.ok(List.of());
    }
}