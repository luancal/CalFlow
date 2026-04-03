package com.luancal.calflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.luancal.calflow.model.Clinica;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class EvolutionService {
    private static final Logger log = LoggerFactory.getLogger(EvolutionService.class);
    @Value("${evolution.api.url:http://localhost:8080}")
    private String evolutionHost;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Random random = new Random();

    public void enviarMensagem(String telefone, String texto, Clinica clinica) {
        try {
            // ✅ PASSO 1: Mostrar "online"
            atualizarPresenca(telefone, "available", clinica);

            // ✅ PASSO 2: Simular tempo de leitura (humano)
            Thread.sleep(800 + random.nextInt(700)); // 800-1500ms

            // ✅ PASSO 3: Mostrar "digitando..."
            atualizarPresenca(telefone, "composing", clinica);

            // ✅ PASSO 4: Delay proporcional ao tamanho da mensagem
            long delay = Math.min(2000, 500 + (texto.length() * 10L));
            Thread.sleep(delay);

            // ✅ PASSO 5: Parar de "digitar" e enviar
            atualizarPresenca(telefone, "paused", clinica);

            enviarTexto(telefone, texto, clinica);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Thread interrompida, enviando mensagem direto");
            enviarTexto(telefone, texto, clinica);
        }
    }

    private void atualizarPresenca(String telefone, String presenca, Clinica clinica) {
        try {
            String url = evolutionHost + "/chat/updatePresence/" + clinica.getNomeInstancia();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", clinica.getApikeyEvolution());
            // Formata número corretamente
            String numeroFormatado = formatarNumeroWhatsApp(telefone);
            Map<String, Object> body = new HashMap<>();
            body.put("number", numeroFormatado); // Só o número, sem @s.whatsapp.net
            body.put("presence", presenca); // "available", "composing", "paused"
            body.put("delay", 1200); // Delay em milissegundos

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Presença atualizada: {} -> {}", numeroFormatado, presenca);
            } else {
                log.warn("Falha ao atualizar presença: status={}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("Erro ao atualizar presença (não crítico): {}", e.getMessage());
        }
    }

    private void enviarTexto(String telefone, String texto, Clinica clinica) {
        try {
            String url = evolutionHost + "/message/sendText/" + clinica.getNomeInstancia();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", clinica.getApikeyEvolution());
            String numeroFormatado = formatarNumeroWhatsApp(telefone);
            Map<String, Object> body = new HashMap<>();
            body.put("number", numeroFormatado);
            body.put("text", texto);
            Map<String, Object> options = new HashMap<>();
            options.put("delay", 500);
            body.put("options", options);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Mensagem enviada: {}", numeroFormatado);
            } else {
                log.error("❌ Falha ao enviar: status={}, body={}",
                        response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("❌ Erro ao enviar mensagem para {}: {}", telefone, e.getMessage(), e);
            throw new RuntimeException("Erro ao enviar mensagem WhatsApp", e);
        }
    }
    private String formatarNumeroWhatsApp(String telefone) {
        String numero = telefone.replaceAll("[^0-9]", "");
        if (numero.startsWith("55") && numero.length() >= 12) {
            return numero;
        }
        if (numero.length() == 10 || numero.length() == 11) {
            return "55" + numero;
        }
        return numero;
    }
}
