package com.luancal.calflow.controller;

import com.luancal.calflow.service.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/webhook")
public class WhatsAppController {

    @Autowired private WhatsAppService whatsAppService;

    private final Set<String> idsProcessados = ConcurrentHashMap.newKeySet();
    private static final Logger logger = LoggerFactory.getLogger(WhatsAppController.class);

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public void receberMensagem(@RequestBody Map<String, Object> payload) {
        try {
            String eventType = (String) payload.get("event");
            if (!"messages.upsert".equalsIgnoreCase(eventType)) return;

            Map<String, Object> data = (Map) payload.get("data");
            Map<String, Object> key = (Map) data.get("key");
            String idMsg = (String) key.get("id");

            // 2. Deduplicação (Evita bot responder 2x)
            if (idsProcessados.contains(idMsg)) return;
            idsProcessados.add(idMsg);
            if (idsProcessados.size() > 2000) idsProcessados.clear();

            // 3. Processamento Async (Libera o Controller)
            whatsAppService.processarMensagemAsync(payload, idMsg);

        } catch (Exception e) {
            logger.error("Erro no recebimento do Webhook: ", e);
        }
    }
}