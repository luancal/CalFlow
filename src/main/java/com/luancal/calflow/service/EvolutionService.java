package com.luancal.calflow.service;

import org.springframework.beans.factory.annotation.Value;
import com.luancal.calflow.model.Clinica;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class EvolutionService {

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
            // ~50ms por caractere, mínimo 1s, máximo 4s
            long delayDigitando = Math.min(4000, Math.max(1000, texto.length() * 50L));
            // Adiciona variação aleatória de ±30%
            long variacao = (long)(delayDigitando * 0.3);
            Thread.sleep(delayDigitando + random.nextInt((int)variacao * 2) - variacao);

            // ✅ PASSO 5: Parar de "digitar" e enviar
            atualizarPresenca(telefone, "paused", clinica);

            enviarTexto(telefone, texto, clinica);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            enviarTexto(telefone, texto, clinica); // Envia mesmo se interrompido
        }
    }

    private void atualizarPresenca(String telefone, String presenca, Clinica clinica) {
        try {
            String url = evolutionHost + "/chat/updatePresence/" + clinica.getNomeInstancia();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", clinica.getApikeyEvolution());

            Map<String, Object> body = new HashMap<>();
            body.put("number", telefone);
            body.put("presence", presenca); // "available", "composing", "paused"

            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            System.err.println("⚠️ Presença não atualizada: " + e.getMessage());
        }
    }

    private void enviarTexto(String telefone, String texto, Clinica clinica) {
        try {
            String url = evolutionHost + "/message/sendText/" + clinica.getNomeInstancia();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", clinica.getApikeyEvolution());

            Map<String, Object> body = new HashMap<>();
            body.put("number", telefone);
            body.put("text", texto);

            Map<String, Object> options = new HashMap<>();
            options.put("delay", 500);
            body.put("options", options);

            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            System.out.println("✅ Msg enviada para: " + telefone);
        } catch (Exception e) {
            System.err.println("❌ Erro ao enviar mensagem: " + e.getMessage());
        }
    }
}
