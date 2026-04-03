package com.luancal.calflow.service;

import com.luancal.calflow.model.Clinica;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EvolutionService {
    private static final Logger log = LoggerFactory.getLogger(EvolutionService.class);

    @Value("${evolution.api.url:http://localhost:8080}")
    private String evolutionHost;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Random random = new Random();

    // Armazena o último envio por número para evitar rajadas (Cooldown)
    private final Map<String, Long> ultimoEnvioPorNumero = new ConcurrentHashMap<>();

    public void enviarMensagem(String telefone, String texto, Clinica clinica) {
        String numeroFormatado = formatarNumeroWhatsApp(telefone);

        try {
            // 1. Respeita o intervalo entre mensagens para o mesmo número
            respeitarCooldown(numeroFormatado);

            // 2. Fica "Online" (Available)
            atualizarPresenca(numeroFormatado, "available", clinica);
            Thread.sleep(800 + random.nextInt(1000)); // Simula tempo de leitura

            // 3. Mostra "Digitando..." (Composing)
            atualizarPresenca(numeroFormatado, "composing", clinica);

            // 4. Delay calculado pelo tamanho do texto (Simula digitação real)
            long delayDigitacao = calcularDelayHumano(texto);
            Thread.sleep(delayDigitacao);

            // 5. Para de digitar e envia
            atualizarPresenca(numeroFormatado, "paused", clinica);
            enviarTexto(numeroFormatado, texto, clinica);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Fluxo interrompido para {}, enviando direto.", numeroFormatado);
            enviarTexto(numeroFormatado, texto, clinica);
        }
    }

    private long calcularDelayHumano(String texto) {
        int tamanho = (texto != null) ? texto.length() : 0;
        // Base de 1s + (15ms por letra) + variação aleatória
        long tempoBase = 1000 + (tamanho * 15L) + random.nextInt(800);
        return Math.min(tempoBase, 5000); // No máximo 5 segundos digitando
    }

    private void respeitarCooldown(String telefone) {
        long agora = System.currentTimeMillis();
        long ultimo = ultimoEnvioPorNumero.getOrDefault(telefone, 0L);
        long intervaloMinimo = 2000L + random.nextInt(1500); // 2 a 3.5 segundos entre msgs

        long falta = intervaloMinimo - (agora - ultimo);
        if (falta > 0) {
            try { Thread.sleep(falta); } catch (InterruptedException ignored) {}
        }
        ultimoEnvioPorNumero.put(telefone, System.currentTimeMillis());
    }

    private void atualizarPresenca(String numero, String presenca, Clinica clinica) {
        try {
            // Endpoint V2: chat/updatePresence/{instance}
            String url = String.format("%s/chat/updatePresence/%s", evolutionHost, clinica.getNomeInstancia());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", clinica.getApikeyEvolution());

            Map<String, Object> body = new HashMap<>();
            body.put("number", numero);
            body.put("presence", presenca);

            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            log.warn("Erro ao atualizar presença: {}", e.getMessage());
        }
    }

    private void enviarTexto(String numero, String texto, Clinica clinica) {
        String url = String.format("%s/message/sendText/%s", evolutionHost, clinica.getNomeInstancia());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", clinica.getApikeyEvolution());

        Map<String, Object> body = new HashMap<>();
        body.put("number", numero);
        body.put("text", texto);

        // Link preview e outras opções (V2)
        Map<String, Object> options = new HashMap<>();
        options.put("delay", 0); // O delay a gente já tratou no Java
        options.put("linkPreview", true);
        body.put("options", options);

        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }

    private String formatarNumeroWhatsApp(String telefone) {
        String numero = telefone.replaceAll("[^0-9]", "");
        // Garante o formato DDI + DDD + Numero (ex: 553299401356)
        if (!numero.startsWith("55")) numero = "55" + numero;
        return numero;
    }
}
