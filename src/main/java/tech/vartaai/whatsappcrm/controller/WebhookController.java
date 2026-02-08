package tech.vartaai.whatsappcrm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.integration.provider.WhatsAppProvider;
import tech.vartaai.whatsappcrm.service.WebhookService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks/whatsapp")
@Slf4j
@CrossOrigin()
public class WebhookController {

    private final WhatsAppProvider provider;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;
    
    @Value("${whatsapp.webhook.verify-token}")
    private String verifyToken;

    @Value("${whatsapp.webhook.auth-token}")
    private String authToken;

    public WebhookController(WhatsAppProvider provider, WebhookService webhookService, ObjectMapper objectMapper) {
        this.provider = provider;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    // Meta verification endpoint
    @GetMapping
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge,
                                         @RequestParam(name = "hub.verify_token", required = false) String token) {
        log.info("Webhook verify mode {} challenge {} token {}", mode, challenge, token);
        if (mode != null && challenge != null && token != null) {
            if ("subscribe".equals(mode) && verifyToken.equals(token)) {
                return ResponseEntity.ok(challenge);
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
        }
        return ResponseEntity.badRequest().body("Missing parameters");
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> receive(@RequestBody Map<String, Object> payload,
                                                       @RequestParam(name = "token", required = false) String token) {
        log.info("Webhook token {}" , token);
        log.info("Webhook payload {}" , payload);
        if (token == null || !authToken.equals(token)) {
             return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        webhookService.processIncoming(payload);
        // Provider-specific handling (delivery receipts, messages)
        provider.handleWebhook(objectMapper.valueToTree(payload));
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}



