package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.dto.WhatsAppOnboardRequest;
import tech.vartaai.whatsappcrm.dto.WhatsAppOnboardResponse;
import tech.vartaai.whatsappcrm.service.WhatsAppOnboardService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/whatsapp")
@Slf4j
@CrossOrigin()
public class WhatsAppOnboardController {

    private final WhatsAppOnboardService whatsAppOnboardService;

    public WhatsAppOnboardController(WhatsAppOnboardService whatsAppOnboardService) {
        this.whatsAppOnboardService = whatsAppOnboardService;
    }

    @PostMapping("/onboard")
    public ResponseEntity<WhatsAppOnboardResponse> onboard(
            @Valid @RequestBody WhatsAppOnboardRequest request,
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(whatsAppOnboardService.onboard(clientId, request));
    }

    @GetMapping("/status")
    public ResponseEntity<WhatsAppOnboardResponse> getStatus(
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(whatsAppOnboardService.getStatus(clientId));
    }

    @PostMapping("/retry-provisioning")
    public ResponseEntity<WhatsAppOnboardResponse> retryProvisioning(
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(whatsAppOnboardService.retryProvisioning(clientId));
    }
}
