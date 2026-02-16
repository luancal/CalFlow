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

@Service
public class EvolutionService {

    @Value("${evolution.api.url:http://localhost:8080}")
    private String evolutionHost;

    public void enviarMensagem(String telefone, String texto, Clinica clinica) {
        RestTemplate restTemplate = new RestTemplate();

        // 1. URL de envio da Evolution
        String url = evolutionHost + "/message/sendText/" + clinica.getNomeInstancia();

        // 2. Headers (Autenticação Global da API)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", clinica.getApikeyEvolution()); // A mesma do docker-compose

        Map<String, Object> body = new HashMap<>();
        body.put("number", telefone);
        body.put("text", texto);

        Map<String, Object> options = new HashMap<>();
        options.put("delay", 2000);
        options.put("presence", "composing");

        body.put("options", options);

        try {
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            System.out.println("✅ Msg enviada via Evolution para: " + telefone);
        } catch (Exception e) {
            System.err.println("❌ Erro Evolution: " + e.getMessage());
        }
    }
}