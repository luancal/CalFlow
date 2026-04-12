package com.luancal.calflow.pagamento.controller;


import com.luancal.calflow.pagamento.domain.*;
import com.luancal.calflow.pagamento.repository.AfiliadoRepository;
import com.luancal.calflow.pagamento.repository.GestorRepository;
import com.luancal.calflow.pagamento.service.EmailService;
import com.luancal.calflow.pagamento.service.NotificacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/parceiros")
@RequiredArgsConstructor
@Slf4j
public class ParceiroController {

    private final AfiliadoRepository afiliadoRepository;
    private final GestorRepository gestorRepository;
    private final EmailService emailService;
    private final NotificacaoService notificacaoService;
    private final PasswordEncoder passwordEncoder;

    // 1. Cadastro de afiliado
    @PostMapping("/afiliado")
    public ResponseEntity<?> cadastrarAfiliado(@RequestBody Map<String, String> req) {
        try {
            String nome = req.get("nome");
            String email = req.get("email");
            String telefone = req.get("telefone");
            String pix = req.get("pixChave");

            // Verificar se já existe
            if (afiliadoRepository.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "E-mail já cadastrado"));
            }

            // Gerar código único
            String primeiroNome = nome.split(" ")[0].toUpperCase().replaceAll("[^A-Z]", "");
            String codigo = "AFF_" + primeiroNome + "_" + (100 + new SecureRandom().nextInt(900));

            while (afiliadoRepository.findByCodigoReferencia(codigo).isPresent()) {
                codigo = "AFF_" + primeiroNome + "_" + (100 + new SecureRandom().nextInt(900));
            }

            // Gerar senha
            String senha = gerarSenha();
            String senhaHash = passwordEncoder.encode(senha);

            Afiliado afiliado = Afiliado.builder()
                    .nome(nome)
                    .email(email.toLowerCase().trim())
                    .telefone(telefone)
                    .pixChave(pix)
                    .codigoReferencia(codigo)
                    .senhaHash(senhaHash)
                    .status(StatusAfiliado.ATIVO)
                    .dataCadastro(LocalDateTime.now())
                    .build();

            afiliado = afiliadoRepository.save(afiliado);

            // Enviar credenciais por e-mail
            emailService.enviarCredenciaisAfiliado(email, nome, codigo, senha);

            // Enviar por WhatsApp
            notificacaoService.enviarCredenciaisAfiliado(telefone, nome, codigo, senha);

            Map<String, Object> response = new HashMap<>();
            response.put("id", afiliado.getId());
            response.put("nome", afiliado.getNome());
            response.put("email", afiliado.getEmail());
            response.put("codigoReferencia", codigo);
            response.put("tipo", "afiliado");
            response.put("mensagem", "Cadastro realizado! Acesse seu painel com o e-mail e a senha enviada.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao cadastrar afiliado", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 2. Candidatura gestor — envia email pra você
    @PostMapping("/gestor-candidatura")
    public ResponseEntity<?> candidatarGestor(@RequestBody Map<String, String> req) {
        try {
            String nome = req.get("nome");
            String email = req.get("email");
            String curriculo = req.get("curriculo");
            String experiencia = req.get("experiencia");
            String meta = req.get("metaAfiliados");

            // Envia email para você (admin)
            String assunto = "Nova candidatura de Gestor CalFlow — " + nome;
            String corpo = String.format("""
                    Nova candidatura de Gestor:
                    
                    Nome: %s
                    Email: %s
                    Telefone: %s
                    PIX: %s
                    Experiência: %s
                    Meta de afiliados: %s
                    
                    Apresentação:
                    %s
                    """, nome, email, req.get("telefone"), req.get("pixChave"),
                    experiencia, meta, curriculo);

            emailService.enviarEmailAdmin("calluann11@gmail.com", assunto, corpo);

            return ResponseEntity.ok(Map.of("success", true, "mensagem", "Candidatura enviada com sucesso!"));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 3. Login do parceiro
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {
        try {
            String email = req.get("email").toLowerCase().trim();
            String senha = req.get("senha");

            // Tenta como afiliado
            Optional<Afiliado> afiliado = afiliadoRepository.findByEmail(email);
            if (afiliado.isPresent()) {
                if (!passwordEncoder.matches(senha, afiliado.get().getSenhaHash())) {
                    return ResponseEntity.status(401).body(Map.of("error", "Senha incorreta"));
                }
                Afiliado a = afiliado.get();
                Map<String, Object> res = new HashMap<>();
                res.put("id", a.getId());
                res.put("nome", a.getNome());
                res.put("email", a.getEmail());
                res.put("pixChave", a.getPixChave());
                res.put("codigoReferencia", a.getCodigoReferencia());
                res.put("tipo", "afiliado");
                return ResponseEntity.ok(res);
            }

            // Tenta como gestor
            Optional<Gestor> gestor = gestorRepository.findByEmail(email);
            if (gestor.isPresent()) {
                if (!passwordEncoder.matches(senha, gestor.get().getSenhaHash())) {
                    return ResponseEntity.status(401).body(Map.of("error", "Senha incorreta"));
                }
                Gestor g = gestor.get();
                Map<String, Object> res = new HashMap<>();
                res.put("id", g.getId());
                res.put("nome", g.getNome());
                res.put("email", g.getEmail());
                res.put("pixChave", g.getPixChave());
                res.put("codigoConvite", g.getCodigoConvite());
                res.put("tipo", "gestor");
                return ResponseEntity.ok(res);
            }

            return ResponseEntity.status(401).body(Map.of("error", "E-mail não encontrado"));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 4. Métricas do parceiro
    @GetMapping("/metricas")
    public ResponseEntity<?> metricas(@RequestParam String email) {
        try {
            Map<String, Object> res = new HashMap<>();

            Optional<Afiliado> afiliado = afiliadoRepository.findByEmail(email);
            if (afiliado.isPresent()) {
                Afiliado a = afiliado.get();
                long totalVendas = a.getVendas().stream().filter(v -> v.isAprovada()).count();
                BigDecimal recorrencia = new BigDecimal("40").multiply(BigDecimal.valueOf(totalVendas));

                res.put("totalVendas", totalVendas);
                res.put("recorrenciaMensal", recorrencia);
                res.put("totalComissoes", calcularTotalComissoes(a.getComissoes()));
                res.put("pendenteMes", calcularPendenteMes(a.getComissoes()));
                res.put("comissoes", a.getComissoes().stream().limit(8).map(c -> {
                    Map<String, Object> cm = new HashMap<>();
                    cm.put("tipo", c.getTipo().name());
                    cm.put("valor", c.getValor().toPlainString());
                    cm.put("status", c.getStatus().name().toLowerCase());
                    cm.put("data", c.getDataGeracao().toString().substring(0, 10));
                    return cm;
                }).collect(java.util.stream.Collectors.toList()));
            }

            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/admin/afiliados")
    public ResponseEntity<?> listarAfiliados() {
        List<Afiliado> afiliados = afiliadoRepository.findAll();
        List<Map<String, Object>> resultado = afiliados.stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("nome", a.getNome());
            m.put("email", a.getEmail());
            m.put("codigoReferencia", a.getCodigoReferencia());
            m.put("pixChave", a.getPixChave());
            m.put("status", a.getStatus().name());
            m.put("gestorNome", a.getGestor() != null ? a.getGestor().getNome() : null);
            m.put("totalVendas", a.getVendas() != null ? a.getVendas().stream().filter(Venda::isAprovada).count() : 0);
            return m;
        }).toList();
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/admin/gestores")
    public ResponseEntity<?> listarGestores() {
        List<Gestor> gestores = gestorRepository.findAll();
        List<Map<String, Object>> resultado = gestores.stream().map(g -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", g.getId());
            m.put("nome", g.getNome());
            m.put("email", g.getEmail());
            m.put("codigoConvite", g.getCodigoConvite());
            m.put("pixChave", g.getPixChave());
            m.put("status", g.getStatus().name());
            m.put("totalAfiliados", g.getAfiliadosRecrutados() != null ? g.getAfiliadosRecrutados().size() : 0);
            return m;
        }).toList();
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/admin/gestor")
    public ResponseEntity<?> criarGestorAdmin(@RequestBody Map<String, String> req) {
        try {
            String nome = req.get("nome");
            String email = req.get("email").toLowerCase().trim();

            if (gestorRepository.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "E-mail já cadastrado"));
            }

            String primeiroNome = nome.split(" ")[0].toUpperCase().replaceAll("[^A-Z]", "");
            String codigo = "GEST_" + primeiroNome + "_" + (100 + new SecureRandom().nextInt(900));

            String senha = gerarSenha();

            Gestor gestor = Gestor.builder()
                    .nome(nome)
                    .email(email)
                    .telefone(req.get("telefone"))
                    .pixChave(req.get("pixChave"))
                    .codigoConvite(codigo)
                    .senhaHash(passwordEncoder.encode(senha))
                    .status(StatusGestor.ATIVO)
                    .dataCadastro(LocalDateTime.now())
                    .build();

            gestorRepository.save(gestor);

            emailService.enviarCredenciaisAfiliado(email, nome, codigo, senha);
            notificacaoService.enviarCredenciaisAfiliado(req.get("telefone"), nome, codigo, senha);

            Map<String, Object> response = new HashMap<>();
            response.put("id", gestor.getId());
            response.put("codigoConvite", codigo);
            response.put("senha", senha);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/admin/afiliado/{id}/reset-senha")
    public ResponseEntity<?> resetarSenhaAfiliado(@PathVariable String id) {
        Afiliado a = afiliadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Afiliado não encontrado"));
        String novaSenha = gerarSenha();
        a.setSenhaHash(passwordEncoder.encode(novaSenha));
        afiliadoRepository.save(a);
        emailService.enviarCredenciaisAfiliado(a.getEmail(), a.getNome(), a.getCodigoReferencia(), novaSenha);
        return ResponseEntity.ok(Map.of("novaSenha", novaSenha));
    }

    @PatchMapping("/admin/afiliado/{id}/status")
    public ResponseEntity<?> statusAfiliado(@PathVariable String id, @RequestBody Map<String, String> req) {
        Afiliado a = afiliadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Afiliado não encontrado"));
        a.setStatus(StatusAfiliado.valueOf(req.get("status")));
        afiliadoRepository.save(a);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/admin/gestor/{id}/status")
    public ResponseEntity<?> statusGestor(@PathVariable String id, @RequestBody Map<String, String> req) {
        Gestor g = gestorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Gestor não encontrado"));
        g.setStatus(StatusGestor.valueOf(req.get("status")));
        gestorRepository.save(g);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private BigDecimal calcularTotalComissoes(List<Comissao> comissoes) {
        return comissoes.stream()
                .filter(c -> "PAGA".equalsIgnoreCase(c.getStatus().name()))
                .map(Comissao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calcularPendenteMes(List<Comissao> comissoes) {
        return comissoes.stream()
                .filter(c -> "PENDENTE".equalsIgnoreCase(c.getStatus().name()))
                .map(Comissao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String gerarSenha() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }
}
