package com.luancal.calflow.pagamento.controller;

import com.luancal.calflow.pagamento.service.EvolutionServiceCF;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/evolution")
public class EvolutionControllerCF {

    @Autowired
    private EvolutionServiceCF evolutionService;

    @GetMapping("/qrcode/{instanceName}")
    public ResponseEntity<?> getQRCode(@PathVariable String instanceName) {
        String qrBase64 = evolutionService.getQRCode(instanceName);
        return ResponseEntity.ok(Map.of("base64", qrBase64));
    }

    @GetMapping("/status/{instanceName}")
    public ResponseEntity<?> getStatus(@PathVariable String instanceName) {
        String state = evolutionService.getConnectionStatus(instanceName);
        return ResponseEntity.ok(Map.of("state", state));
    }
}
