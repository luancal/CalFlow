package com.luancal.calflow.pagamento.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

@Service
public class EvolutionServiceCF {

    private static final Logger log = LoggerFactory.getLogger(EvolutionServiceCF.class);

    @Value("${evolution.api.url}")
    private String evolutionUrl;

    @Value("${evolution.api.key}")
    private String evolutionApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> criarInstancia(String instanceName) {
        String url = evolutionUrl + "/instance/create";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", evolutionApiKey);

        // Token da instância (usado para autenticação interna da Evolution)
        String token = gerarTokenAleatorio(32);

        Map<String, Object> body = Map.of(
                "instanceName", instanceName,
                "token", token,
                "qrcode", true,
                "integration", "WHATSAPP-BAILEYS",
                "groupsIgnore", true,
                "alwaysOnline", false,
                "readMessages", false,
                "readStatus", false,
                "syncFullHistory", false
        );

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            log.info("✅ Instância criada: {}", instanceName);

            // Configura o Webhook logo após criar
            configurarWebhook(instanceName);

            return response.getBody();
        } catch (Exception e) {
            log.error("❌ Erro ao criar instância {}: {}", instanceName, e.getMessage());
            throw new RuntimeException("Falha na criação da instância");
        }
    }

    public void configurarWebhook(String instanceName) {
        // Na V2 o endpoint de setar webhook é via POST ou no create.
        // Aqui usamos o padrão da V2:
        String url = evolutionUrl + "/webhook/set/" + instanceName;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", evolutionApiKey);

        Map<String, Object> body = Map.of(
                "enabled", true,
                "url", "https://calflow.app.br/api/webhook/whatsapp",
                "webhookByEvents", false,
                "events", List.of("MESSAGES_UPSERT", "CONNECTION_UPDATE", "QRCODE_UPDATED")
        );

        try {
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            log.info("🔗 Webhook configurado para: {}", instanceName);
        } catch (Exception e) {
            log.warn("⚠️ Não foi possível setar o webhook agora: {}", e.getMessage());
        }
    }

    public String getQRCode(String instanceName) {
        // Endpoint correto V2
        String url = evolutionUrl + "/instance/connect/" + instanceName;

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", evolutionApiKey);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map body = response.getBody();
            // A Evolution retorna o base64 dentro de body -> code ou base64
            if (body != null) {
                return (String) body.get("base64");
            }
        } catch (Exception e) {
            log.error("❌ Erro ao buscar QR Code: {}", e.getMessage());
        }
        return null;
    }
    public String getConnectionStatus(String instanceName) {
        String url = evolutionUrl + "/instance/connectionState/" + instanceName;

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", evolutionApiKey);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map body = response.getBody();

            // A Evolution retorna o state dentro do objeto 'instance'
            if (body != null && body.containsKey("instance")) {
                Map instanceData = (Map) body.get("instance");
                return (String) instanceData.get("state");
            }
        } catch (Exception e) {
            log.error("❌ Erro ao buscar status da instância {}: {}", instanceName, e.getMessage());
        }

        return "DISCONNECTED";
    }

    public void enviarMensagem(String instanceName, String telefone, String mensagem) {
        String url = evolutionUrl + "/message/sendText/" + instanceName;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", evolutionApiKey);

        // LIMPEZA DO NÚMERO (Removendo o @s.whatsapp.net para a V2)
        String numeroLimpo = telefone.replaceAll("\\D", "");
        if (!numeroLimpo.startsWith("55")) numeroLimpo = "55" + numeroLimpo;

        Map<String, Object> body = Map.of(
                "number", numeroLimpo,
                "text", mensagem
        );

        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
        log.info("🚀 Mensagem enviada via {}: {}", instanceName, numeroLimpo);
    }

    private String gerarTokenAleatorio(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        return random.ints(length, 0, chars.length())
                .mapToObj(chars::charAt)
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString();
    }
}

