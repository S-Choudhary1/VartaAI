package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.vartaai.whatsappcrm.dto.SendMessageRequest;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.exception.ApiException;
import tech.vartaai.whatsappcrm.exception.MediaApiException;
import tech.vartaai.whatsappcrm.integration.provider.SendResponse;
import tech.vartaai.whatsappcrm.repository.ContactRepository;
import tech.vartaai.whatsappcrm.repository.MessageRepository;
import tech.vartaai.whatsappcrm.service.MessageService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@Slf4j
@CrossOrigin()
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
        } catch (ApiException e) {
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("error", e.getErrorCode(), "message", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Error sending message", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "INTERNAL_ERROR", "message", "Failed to send message."));
        }
    }

    @PostMapping(value = "/send-media", consumes = {"multipart/form-data"})
    public ResponseEntity<?> sendMedia(@RequestParam("to") String to,
                                       @RequestParam("messageType") Message.MessageType messageType,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "caption", required = false) String caption,
                                       @RequestHeader("X-Client-Id") UUID clientId) {
        try {
            SendResponse response = messageService.sendMediaMessage(clientId, to, messageType, file, caption);
            return ResponseEntity.ok(response);
        } catch (ApiException e) {
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("error", e.getErrorCode(), "message", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Error sending media message", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Failed to send media message."));
        }
    }

    @GetMapping("/{messageId}/media")
    public ResponseEntity<?> getMessageMedia(@PathVariable UUID messageId,
                                             @RequestParam(defaultValue = "inline") String disposition,
                                             @RequestHeader("X-Client-Id") UUID clientId) {
        boolean attachment;
        if ("inline".equalsIgnoreCase(disposition)) {
            attachment = false;
        } else if ("attachment".equalsIgnoreCase(disposition)) {
            attachment = true;
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "INVALID_DISPOSITION",
                            "message", "disposition must be inline or attachment."));
        }

        try {
            MessageService.MessageMediaResult media = messageService.getMessageMedia(messageId, clientId);

            ContentDisposition contentDisposition = attachment
                    ? ContentDisposition.attachment().filename(media.filename(), StandardCharsets.UTF_8).build()
                    : ContentDisposition.inline().filename(media.filename(), StandardCharsets.UTF_8).build();

            MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
            try {
                contentType = MediaType.parseMediaType(media.mimeType());
            } catch (Exception ignored) {
                // Fall back to octet-stream if provider mime type is invalid.
            }

            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                    .body(media.content());
        } catch (MediaApiException ex) {
            return ResponseEntity.status(ex.getStatus())
                    .body(Map.of("error", ex.getErrorCode(), "message", ex.getMessage()));
        }
    }
}
