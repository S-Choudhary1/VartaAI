package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.dto.SendMessageRequest;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.integration.provider.SendResponse;
import tech.vartaai.whatsappcrm.repository.ContactRepository;
import tech.vartaai.whatsappcrm.repository.MessageRepository;
import tech.vartaai.whatsappcrm.service.MessageService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@Slf4j
public class MessageController {

    private final MessageService messageService;
    private final MessageRepository messageRepository;
    private final ContactRepository contactRepository;

    public MessageController(MessageService messageService, 
                             MessageRepository messageRepository,
                             ContactRepository contactRepository) {
        this.messageService = messageService;
        this.messageRepository = messageRepository;
        this.contactRepository = contactRepository;
    }

    @GetMapping("/history")
    public ResponseEntity<List<Message>> getHistory(@RequestParam String phone,
                                                    @RequestHeader("X-Client-Id") UUID clientId) {
        return contactRepository.findByPhoneAndClient_Id(phone, clientId)
                .map(contact -> ResponseEntity.ok(messageRepository.findByContactIdAndClient_Id(contact.getId().toString(), clientId)))
                .orElse(ResponseEntity.ok(List.of()));
    }

    @PostMapping("/send")
    public ResponseEntity<?> send(@Valid @RequestBody SendMessageRequest request,
                                             @RequestHeader("X-Client-Id") UUID clientId) {
        try {
            SendResponse response = messageService.sendMessage(request, clientId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Error sending message", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", e.getMessage(), "status", "failed"));
        }
    }
}
