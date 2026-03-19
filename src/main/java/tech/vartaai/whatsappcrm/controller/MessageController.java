package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.vartaai.whatsappcrm.dto.SendMessageRequest;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.integration.provider.SendResponse;
import tech.vartaai.whatsappcrm.repository.ContactRepository;
import tech.vartaai.whatsappcrm.repository.MessageRepository;
import tech.vartaai.whatsappcrm.service.MessageService;

import java.nio.charset.StandardCharsets;
import java.util.List;
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
    public ResponseEntity<Page<Message>> getHistory(@RequestParam String phone,
                                                    @RequestHeader("X-Client-Id") UUID clientId,
                                                    @PageableDefault(size = 50) Pageable pageable) {
        return contactRepository.findByPhoneAndClient_Id(phone, clientId)
                .map(contact -> ResponseEntity.ok(messageRepository.findByContactIdAndClient_Id(contact.getId(), clientId, pageable)))
                .orElse(ResponseEntity.ok(Page.empty(pageable)));
    }

    @PostMapping("/send")
    public ResponseEntity<SendResponse> send(@Valid @RequestBody SendMessageRequest request,
                                             @RequestHeader("X-Client-Id") UUID clientId) {
        SendResponse response = messageService.sendMessage(request, clientId);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/send-media", consumes = {"multipart/form-data"})
    public ResponseEntity<SendResponse> sendMedia(@RequestParam("to") String to,
                                                   @RequestParam("messageType") Message.MessageType messageType,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "caption", required = false) String caption,
                                                   @RequestHeader("X-Client-Id") UUID clientId) {
        SendResponse response = messageService.sendMediaMessage(clientId, to, messageType, file, caption);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{messageId}/media")
    public ResponseEntity<byte[]> getMessageMedia(@PathVariable UUID messageId,
                                                   @RequestParam(defaultValue = "inline") String disposition,
                                                   @RequestHeader("X-Client-Id") UUID clientId) {
        boolean attachment = "attachment".equalsIgnoreCase(disposition);

        MessageService.MessageMediaResult media = messageService.getMessageMedia(messageId, clientId);

        ContentDisposition contentDisposition = attachment
                ? ContentDisposition.attachment().filename(media.filename(), StandardCharsets.UTF_8).build()
                : ContentDisposition.inline().filename(media.filename(), StandardCharsets.UTF_8).build();

        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            contentType = MediaType.parseMediaType(media.mimeType());
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .body(media.content());
    }
}
