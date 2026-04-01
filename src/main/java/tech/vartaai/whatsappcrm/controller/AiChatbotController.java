package tech.vartaai.whatsappcrm.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.entity.AiChatbotConfig;
import tech.vartaai.whatsappcrm.entity.AiConversation;
import tech.vartaai.whatsappcrm.entity.AiMessageLog;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.exception.ApiException;
import tech.vartaai.whatsappcrm.repository.ClientRepository;
import tech.vartaai.whatsappcrm.service.AiChatbotService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai-chatbot")
@Slf4j
@CrossOrigin()
public class AiChatbotController {

    private final AiChatbotService aiChatbotService;
    private final ClientRepository clientRepository;

    public AiChatbotController(AiChatbotService aiChatbotService,
                               ClientRepository clientRepository) {
        this.aiChatbotService = aiChatbotService;
        this.clientRepository = clientRepository;
    }

    @GetMapping("/config")
    public ResponseEntity<AiChatbotConfig> getConfig(
            @RequestHeader("X-Client-Id") UUID clientId) {
        log.info("AI_CHATBOT_GET_CONFIG clientId={}", clientId);
        AiChatbotConfig config = aiChatbotService.getConfig(clientId);
        if (config == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CONFIG_NOT_FOUND",
                    "AI chatbot config not found.");
        }
        return ResponseEntity.ok(config);
    }

    @PostMapping("/config")
    public ResponseEntity<AiChatbotConfig> saveConfig(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestBody AiChatbotConfig config) {
        log.info("AI_CHATBOT_SAVE_CONFIG clientId={} provider={} model={}",
                clientId, config.getProvider(), config.getModel());

        // Ensure the client entity is set for new configs
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND",
                        "Client not found."));
        config.setClient(client);

        AiChatbotConfig saved = aiChatbotService.saveConfig(clientId, config);
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/config/toggle")
    public ResponseEntity<Void> toggleChatbot(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        log.info("AI_CHATBOT_TOGGLE clientId={} enabled={}", clientId, enabled);
        aiChatbotService.toggleEnabled(clientId, enabled);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testChat(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Message is required.");
        }
        log.info("AI_CHATBOT_TEST clientId={} msgLength={}", clientId, message.length());

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND",
                        "Client not found."));

        return ResponseEntity.ok(aiChatbotService.testChat(client, message));
    }

    @GetMapping("/conversations")
    public ResponseEntity<Page<AiConversation>> getConversations(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("AI_CHATBOT_LIST_CONVERSATIONS clientId={}", clientId);
        AiChatbotConfig config = aiChatbotService.getConfig(clientId);
        if (config == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CONFIG_NOT_FOUND",
                    "AI chatbot config not found.");
        }
        return ResponseEntity.ok(
                aiChatbotService.getConversations(config.getId(), PageRequest.of(page, size)));
    }

    @GetMapping("/conversations/{convId}/messages")
    public ResponseEntity<List<AiMessageLog>> getConversationMessages(
            @PathVariable UUID convId) {
        log.info("AI_CHATBOT_GET_MESSAGES conversationId={}", convId);
        return ResponseEntity.ok(aiChatbotService.getConversationMessages(convId));
    }

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics(
            @RequestHeader("X-Client-Id") UUID clientId) {
        log.info("AI_CHATBOT_ANALYTICS clientId={}", clientId);
        return ResponseEntity.ok(aiChatbotService.getAnalytics(clientId));
    }
}
