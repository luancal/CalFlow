package com.luancal.calflow.pagamento.controller;

import com.luancal.calflow.pagamento.config_dto.LoginRequest;
import com.luancal.calflow.pagamento.domain.ClienteCalFlow;
import com.luancal.calflow.pagamento.repository.ClienteCalFlowRepository;
import com.luancal.calflow.pagamento.repository.ClienteRepository;
import com.luancal.calflow.pagamento.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final ClienteCalFlowRepository clienteRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        ClienteCalFlow cliente = clienteRepo.findByUsuario(request.getUsuario())
                .orElseThrow(() -> new RuntimeException("Credenciais inválidas"));

        if (!passwordEncoder.matches(request.getSenha(), cliente.getSenhaHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Credenciais inválidas"));
        }

        if (!"ativo".equalsIgnoreCase(cliente.getStatus())) {
            return ResponseEntity.status(403).body(Map.of("error", "Conta suspensa"));
        }

        String token = jwtService.gerarToken(cliente);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "clienteId", cliente.getId(),
                "nome", cliente.getNome(),
                "negocio", cliente.getNomeNegocio(),
                "usuario", cliente.getUsuario(),
                "tipo", cliente.getTipo()
        ));
    }
}

