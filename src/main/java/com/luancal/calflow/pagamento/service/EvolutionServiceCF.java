package com.luancal.calflow.pagamento.service;

import com.luancal.calflow.service.EvolutionService;

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

    private final RestTemplate restTemplate;

    public EvolutionServiceCF() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Cria instância na Evolution API
     */
    public Map<String, Object> criarInstancia(String instanceName) {
        String url = evolutionUrl + "/instance/create";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", evolutionApiKey);

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

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            log.info("Instância criada: {}", instanceName);

            // Configurar webhook
            configurarWebhook(instanceName);

            return response.getBody();
        } catch (Exception e) {
            log.error("Erro ao criar instância: {}", instanceName, e);
            throw new RuntimeException("Erro ao criar instância Evolution", e);
        }
    }

    /**
     * Configura webhook para receber mensagens
     */
    public void configurarWebhook(String instanceName) {
        String url = evolutionUrl + "/webhook/set/" + instanceName;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", evolutionApiKey);

        Map<String, Object> body = Map.of(
                "enabled", true,
                "url", "https://calflow.app.br/api/webhook/whatsapp",
                "webhookByEvents", false,
                "events", List.of(
                        "messages.upsert",
                        "connection.update",
                        "qrcode.updated"
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.exchange(url, HttpMethod.PUT, request, Map.class);
        log.info("Webhook configurado: {}", instanceName);
    }

    /**
     * Busca QR Code da instância
     */
    public String getQRCode(String instanceName) {
        String url = evolutionUrl + "/instance/connect/" + instanceName;

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", evolutionApiKey);

        HttpEntity<?> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, Map.class);
            Map body = response.getBody();
            if (body != null && body.containsKey("base64")) {
                return (String) body.get("base64");
            }
            return null;
        } catch (Exception e) {
            log.error("Erro ao buscar QR: {}", instanceName, e);
            return null;
        }
    }

    /**
     * Verifica status da conexão
     */
    public String getConnectionStatus(String instanceName) {
        String url = evolutionUrl + "/instance/connectionState/" + instanceName;

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", evolutionApiKey);

        try {
            HttpEntity<?> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, Map.class);
            Map body = response.getBody();
            if (body != null && body.containsKey("state")) {
                return (String) body.get("state");
            }
            return "close";
        } catch (Exception e) {
            return "close";
        }
    }

    /**
     * Envia mensagem de texto
     */
    public void enviarMensagem(String instanceName, String telefone, String mensagem) {
        String url = evolutionUrl + "/message/sendText/" + instanceName;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", evolutionApiKey);

        String jid = telefone.replaceAll("\\D", "");
        if (!jid.startsWith("55")) jid = "55" + jid;
        jid += "@s.whatsapp.net";

        Map<String, Object> body = Map.of(
                "number", jid,
                "text", mensagem
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(url, request, Map.class);
        log.info("Mensagem enviada: telefone={}", telefone);
    }

    private String gerarTokenAleatorio(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
