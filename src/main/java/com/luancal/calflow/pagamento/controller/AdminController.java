package com.luancal.calflow.pagamento.controller;

import com.luancal.calflow.pagamento.domain.ClienteCalFlow;
import com.luancal.calflow.pagamento.repository.ClienteCalFlowRepository;
import com.luancal.calflow.pagamento.service.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final JwtService jwtService;
    private final ClienteCalFlowRepository clienteRepo;
    private final PasswordEncoder passwordEncoder;

    private boolean isAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return false;
        try {
            Claims claims = jwtService.validarToken(authHeader.substring(7));
            return "admin".equalsIgnoreCase((String) claims.get("tipo"));
        } catch (Exception e) {
            return false;
        }
    }

    @GetMapping("/clientes")
    public ResponseEntity<?> listarClientes(@RequestHeader("Authorization") String authHeader) {
        if (!isAdmin(authHeader)) return ResponseEntity.status(403).body(Map.of("error", "Sem permissão"));

        List<ClienteCalFlow> clientes = clienteRepo.findAll();
        List<Map<String, Object>> resultado = clientes.stream()
                .filter(c -> !"admin".equalsIgnoreCase(c.getTipo()))
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getId());
                    m.put("usuario", c.getUsuario());
                    m.put("nome", c.getNome());
                    m.put("email", c.getEmail());
                    m.put("negocio", c.getNomeNegocio() != null ? c.getNomeNegocio() : "");
                    m.put("plano", c.getPlano() != null ? c.getPlano() : "mensal");
                    m.put("status", c.getStatus());
                    m.put("nomeInstancia", c.getNomeInstancia() != null ? c.getNomeInstancia() : "");
                    m.put("dataVencimento", c.getDataVencimento() != null ? c.getDataVencimento().toString() : "");
                    return m;
                }).toList();

        return ResponseEntity.ok(resultado);
    }

    @PutMapping("/clientes/{id}")
    public ResponseEntity<?> editarCliente(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        if (!isAdmin(authHeader)) return ResponseEntity.status(403).body(Map.of("error", "Sem permissão"));

        ClienteCalFlow cliente = clienteRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        if (body.containsKey("nome")) cliente.setNome((String) body.get("nome"));
        if (body.containsKey("negocio")) cliente.setNomeNegocio((String) body.get("negocio"));
        if (body.containsKey("plano")) cliente.setPlano((String) body.get("plano"));

        clienteRepo.save(cliente);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/clientes/{id}/status")
    public ResponseEntity<?> alterarStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        if (!isAdmin(authHeader)) return ResponseEntity.status(403).body(Map.of("error", "Sem permissão"));

        ClienteCalFlow cliente = clienteRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        cliente.setStatus(body.get("status"));
        clienteRepo.save(cliente);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/clientes/{id}/reset-senha")
    public ResponseEntity<?> resetarSenha(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id) {
        if (!isAdmin(authHeader)) return ResponseEntity.status(403).body(Map.of("error", "Sem permissão"));

        ClienteCalFlow cliente = clienteRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        String novaSenha = gerarSenha();
        cliente.setSenhaHash(passwordEncoder.encode(novaSenha));
        clienteRepo.save(cliente);

        return ResponseEntity.ok(Map.of("novaSenha", novaSenha));
    }

    @PostMapping("/clientes")
    public ResponseEntity<?> criarCliente(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {
        if (!isAdmin(authHeader)) return ResponseEntity.status(403).body(Map.of("error", "Sem permissão"));

        String nome = (String) body.get("nome");
        String negocio = (String) body.get("negocio");
        String email = (String) body.getOrDefault("email", "");
        String plano = (String) body.getOrDefault("plano", "mensal");

        String usuario = nome.split(" ")[0].toLowerCase().replaceAll("[^a-z0-9]", "")
                + (1000 + new SecureRandom().nextInt(9000));
        String senha = gerarSenha();

        ClienteCalFlow cliente = ClienteCalFlow.builder()
                .usuario(usuario)
                .senhaHash(passwordEncoder.encode(senha))
                .nome(nome)
                .email(email)
                .telefone("")
                .nomeNegocio(negocio)
                .tipo("cliente")
                .status("ativo")
                .plano(plano)
                .nomeInstancia("calflow_" + usuario)
                .dataCriacao(LocalDateTime.now())
                .build();

        clienteRepo.save(cliente);

        return ResponseEntity.ok(Map.of(
                "usuario", usuario,
                "senha", senha,
                "nome", nome
        ));
    }

    private String gerarSenha() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }
}