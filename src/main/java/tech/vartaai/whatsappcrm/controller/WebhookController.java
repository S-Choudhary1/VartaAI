package tech.vartaai.whatsappcrm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.config.WhatsAppProperties;
import tech.vartaai.whatsappcrm.integration.provider.WhatsAppProvider;
import tech.vartaai.whatsappcrm.service.WebhookService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks/whatsapp")
@Slf4j
@CrossOrigin()
public class WebhookController {

    private final WhatsAppProvider provider;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final WhatsAppProperties whatsAppProperties;

    @Value("${whatsapp.webhook.verify-token}")
    private String verifyToken;

    @Value("${whatsapp.webhook.auth-token}")
    private String authToken;

    public WebhookController(WhatsAppProvider provider, WebhookService webhookService,
                             ObjectMapper objectMapper, WhatsAppProperties whatsAppProperties) {
        this.provider = provider;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
        this.whatsAppProperties = whatsAppProperties;
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
    public ResponseEntity<Map<String, String>> receive(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestParam(name = "token", required = false) String token) {

        // Verify webhook signature
        if (!verifySignature(rawBody, signatureHeader)) {
            log.warn("Webhook signature verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> payload;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(rawBody, Map.class);
            payload = parsed;
        } catch (Exception e) {
            log.error("Failed to parse webhook payload", e);
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid JSON"));
        }

        log.info("Webhook token {}", token);
        log.info("Webhook payload {}", payload);
        webhookService.processWebhook(payload);
        // Provider-specific handling (delivery receipts, messages)
        provider.handleWebhook(objectMapper.valueToTree(payload));
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    private boolean verifySignature(String rawBody, String signatureHeader) {
        String appSecret = whatsAppProperties.getAppSecret();
        if (appSecret == null || appSecret.isBlank()) {
            log.warn("whatsapp.providers.meta.appSecret is not configured — skipping webhook signature verification");
            return true;
        }

        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            log.warn("Missing or malformed X-Hub-Signature-256 header");
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = "sha256=" + HexFormat.of().formatHex(hash);
            return expectedSignature.equals(signatureHeader);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute HMAC-SHA256 for webhook signature verification", e);
            return false;
        }
    }
}
