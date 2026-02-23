package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.vartaai.whatsappcrm.dto.SendMessageRequest;
import tech.vartaai.whatsappcrm.exception.ApiException;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Contact;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.entity.Template;
import tech.vartaai.whatsappcrm.exception.MediaApiException;
import tech.vartaai.whatsappcrm.integration.provider.MediaDownload;
import tech.vartaai.whatsappcrm.integration.provider.SendResponse;
import tech.vartaai.whatsappcrm.integration.provider.WhatsAppProvider;
import tech.vartaai.whatsappcrm.repository.ClientRepository;
import tech.vartaai.whatsappcrm.repository.ContactRepository;
import tech.vartaai.whatsappcrm.repository.MessageRepository;
import tech.vartaai.whatsappcrm.repository.TemplateRepository;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class MessageService {
    private static final EnumSet<Message.MessageType> QUICK_SEND_TYPES = EnumSet.of(
            Message.MessageType.TEXT,
            Message.MessageType.TEMPLATE
    );
    private static final EnumSet<Message.MessageType> MEDIA_MESSAGE_TYPES = EnumSet.of(
            Message.MessageType.IMAGE,
            Message.MessageType.VIDEO,
            Message.MessageType.AUDIO,
            Message.MessageType.DOCUMENT,
            Message.MessageType.STICKER
    );
    private static final EnumSet<Message.MessageType> SEND_MEDIA_TYPES = EnumSet.of(
            Message.MessageType.IMAGE,
            Message.MessageType.VIDEO,
            Message.MessageType.DOCUMENT
    );
    private static final Set<String> ALLOWED_DOCUMENT_MIME_TYPES = new HashSet<>(Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv"
    ));

    private final WhatsAppProvider provider;
    private final TemplateRepository templateRepository;
    private final MessageRepository messageRepository;
    private final ContactRepository contactRepository;
    private final ClientRepository clientRepository;
    private final ObjectMapper objectMapper;

    @Value("${whatsapp.providers.meta.phoneNumberId}")
    private String defaultPhoneNumberId;
    
    @Value("${whatsapp.providers.meta.accessToken}")
    private String defaultAccessToken;

    public MessageService(WhatsAppProvider provider, 
                          TemplateRepository templateRepository,
                          MessageRepository messageRepository,
                          ContactRepository contactRepository,
                          ClientRepository clientRepository,
                          ObjectMapper objectMapper) {
        this.provider = provider;
        this.templateRepository = templateRepository;
        this.messageRepository = messageRepository;
        this.contactRepository = contactRepository;
        this.clientRepository = clientRepository;
        this.objectMapper = objectMapper;
    }

    public SendResponse sendMessage(SendMessageRequest request, UUID clientId) {
        log.info("Processing send message request to: {}, clientId: {}", request.getTo(), clientId);
        if (request.getMessageType() == null || !QUICK_SEND_TYPES.contains(request.getMessageType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "messageType must be TEXT or TEMPLATE.");
        }
        
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND", "Client not found."));

        // Use default credentials if client specific ones are missing (for dev/demo)
        // In prod, each client should have their own
        if (client.getPhoneNumberId() == null) client.setPhoneNumberId(defaultPhoneNumberId);
        if (client.getAccessToken() == null) client.setAccessToken(defaultAccessToken);

        Contact contact = contactRepository.findByPhoneAndClient_Id(request.getTo(), clientId)
                .orElseGet(() -> {
                    Contact newContact = new Contact();
                    newContact.setClient(client);
                    newContact.setPhone(request.getTo());
                    newContact.setName(request.getTo()); 
                    return contactRepository.save(newContact);
                });

        SendResponse resp;
        Message message = new Message();
        message.setClient(client);
        message.setContactId(contact.getId().toString());
        message.setDirection(Message.Direction.OUTGOING);
        message.setProvider("META");
        message.setCampaignId(request.getCampaignId());
        message.setStatus(Message.Status.QUEUED);

        try {
            if (request.getMessageType() == Message.MessageType.TEMPLATE) {
                if (request.getTemplateId() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                            "templateId is required for TEMPLATE messageType.");
                }
                resp = sendTemplateMessage(client, request, message);
            } else {
                if (request.getText() == null || request.getText().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                            "text is required for TEXT messageType.");
                }
                resp = sendTextMessage(client, request, message);
            }
            
            message.setStatus(Message.Status.SENT);
            if (resp != null) {
                message.setProviderMessageId(resp.getProviderMessageId());
            }
            messageRepository.save(message);
            if (resp == null) {
                resp = new SendResponse(message.getProviderMessageId(), "SENT");
            }
            resp.setId(message.getId().toString());
            return resp;

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            message.setStatus(Message.Status.FAILED);
            messageRepository.save(message);
            log.error("Failed to send message to {} {}", request.getTo(), e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PROVIDER_ERROR",
                    "Failed to send message.");
        }
    }

    public SendResponse sendMediaMessage(UUID clientId, String to, Message.MessageType messageType,
                                         MultipartFile file, String caption) {
        if (messageType == null || !SEND_MEDIA_TYPES.contains(messageType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "messageType must be IMAGE, VIDEO, or DOCUMENT.");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "file is required.");
        }

        String mimeType = firstNonBlank(file.getContentType(), "");
        validateMediaMimeType(messageType, mimeType);

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND", "Client not found."));
        if (client.getPhoneNumberId() == null || client.getPhoneNumberId().isBlank()) {
            client.setPhoneNumberId(defaultPhoneNumberId);
        }
        if (client.getAccessToken() == null || client.getAccessToken().isBlank()) {
            client.setAccessToken(defaultAccessToken);
        }

        Contact contact = contactRepository.findByPhoneAndClient_Id(to, clientId)
                .orElseGet(() -> {
                    Contact newContact = new Contact();
                    newContact.setClient(client);
                    newContact.setPhone(to);
                    newContact.setName(to);
                    return contactRepository.save(newContact);
                });

        Message message = new Message();
        message.setClient(client);
        message.setContactId(contact.getId().toString());
        message.setDirection(Message.Direction.OUTGOING);
        message.setProvider("META");
        message.setStatus(Message.Status.QUEUED);
        message.setMessageType(messageType);

        try {
            SendResponse providerResp = provider.sendMedia(
                    client,
                    to,
                    messageType,
                    file.getBytes(),
                    firstNonBlank(file.getOriginalFilename(), "upload.bin"),
                    mimeType,
                    caption
            );

            message.setProviderMessageId(providerResp.getProviderMessageId());
            message.setStatus(Message.Status.SENT);
            Map<String, Object> mediaBody = new HashMap<>();
            mediaBody.put("id", providerResp.getMediaId());
            mediaBody.put("mime_type", mimeType);
            if (firstNonBlank(providerResp.getFilename(), file.getOriginalFilename()) != null) {
                mediaBody.put("filename", firstNonBlank(providerResp.getFilename(), file.getOriginalFilename()));
            }
            if (caption != null && !caption.isBlank()) {
                mediaBody.put("caption", caption);
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", messageType.name().toLowerCase());
            payload.put(messageType.name().toLowerCase(), mediaBody);
            message.setPayloadJson(objectMapper.writeValueAsString(payload));
            messageRepository.save(message);

            providerResp.setId(message.getId().toString());
            providerResp.setStatus("SENT");
            return providerResp;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            message.setStatus(Message.Status.FAILED);
            messageRepository.save(message);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PROVIDER_ERROR",
                    "Failed to send media message.");
        }
    }

    public MessageMediaResult getMessageMedia(UUID messageId, UUID clientId) {
        if (!messageRepository.existsById(messageId)) {
            throw new MediaApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND",
                    "Media is unavailable for this message.");
        }

        Message message = messageRepository.findByIdAndClient_Id(messageId, clientId)
                .orElseThrow(() -> new MediaApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "You cannot access media for this tenant."));

        if (message.getMessageType() == null || !MEDIA_MESSAGE_TYPES.contains(message.getMessageType())) {
            throw new MediaApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MESSAGE_TYPE",
                    "Message type does not support media.");
        }

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new MediaApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND",
                        "Media is unavailable for this message."));
        if ((client.getAccessToken() == null || client.getAccessToken().isBlank())
                && defaultAccessToken != null && !defaultAccessToken.isBlank()) {
            client.setAccessToken(defaultAccessToken);
        }

        MediaMeta mediaMeta = resolveMediaMeta(message);
        if (mediaMeta.mediaId() == null || mediaMeta.mediaId().isBlank()) {
            throw new MediaApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND",
                    "Media is unavailable for this message.");
        }

        MediaDownload download = provider.downloadMedia(client, mediaMeta.mediaId(), mediaMeta.mimeType(), mediaMeta.filename());
        return new MessageMediaResult(download.getContent(), download.getMimeType(), download.getFilename());
    }

    private SendResponse sendTemplateMessage(Client client, SendMessageRequest request, Message message) throws Exception {
        Template template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "Template not found."));
        
        if (!template.getClient().getId().equals(client.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Template not found or access denied.");
        }
        if (template.getStatus() != Template.TemplateStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_NOT_APPROVED", "Template is not approved.");
        }

        Map<String, String> vars = request.getVariables() != null ? request.getVariables() : Map.of();


        // Prepare payload log
        String payload = objectMapper.writeValueAsString(Map.of(
            "type", "template",
            "templateId", request.getTemplateId(),
            "templateName", template.getName(),
            "variables", vars,
            "body", template.getContentJson()
        ));
        message.setPayloadJson(payload);
        message.setMessageType(Message.MessageType.TEMPLATE);

        return provider.sendTemplate(client, request.getTo(), template, vars);
    }

    private SendResponse sendTextMessage(Client client, SendMessageRequest request, Message message) throws Exception {
        String textBody = request.getText();
        
        // Prepare payload log
        String payload = objectMapper.writeValueAsString(Map.of(
            "type", "text",
            "body", textBody
        ));
        message.setPayloadJson(payload);
        message.setMessageType(Message.MessageType.TEXT);

        return provider.sendText(client, request.getTo(), textBody);
    }

    private MediaMeta resolveMediaMeta(Message message) {
        String typeKey = message.getMessageType().name().toLowerCase();

        MediaMeta fromPayload = extractMediaMetaFromJson(message.getPayloadJson(), typeKey);
        if (fromPayload.mediaId() != null && !fromPayload.mediaId().isBlank()) {
            return fromPayload;
        }

        MediaMeta fromResponse = extractMediaMetaFromJson(message.getResponseJson(), typeKey);
        if (fromResponse.mediaId() != null && !fromResponse.mediaId().isBlank()) {
            return fromResponse;
        }

        return new MediaMeta(null, null, null);
    }

    private MediaMeta extractMediaMetaFromJson(String rawJson, String typeKey) {
        if (rawJson == null || rawJson.isBlank()) {
            return new MediaMeta(null, null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode typeNode = root.path(typeKey);

            String mediaId = firstNonBlank(
                    typeNode.path("id").asText(null),
                    root.path("id").asText(null)
            );
            String mimeType = firstNonBlank(
                    typeNode.path("mime_type").asText(null),
                    typeNode.path("mimeType").asText(null),
                    root.path("mime_type").asText(null),
                    root.path("mimeType").asText(null)
            );
            String filename = firstNonBlank(
                    typeNode.path("filename").asText(null),
                    root.path("filename").asText(null)
            );
            return new MediaMeta(mediaId, mimeType, filename);
        } catch (Exception ex) {
            log.warn("Failed to parse media metadata JSON: {}", ex.getMessage());
            return new MediaMeta(null, null, null);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void validateMediaMimeType(Message.MessageType messageType, String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_MEDIA_TYPE",
                    "File content type is required.");
        }
        switch (messageType) {
            case IMAGE:
                if (!mimeType.startsWith("image/")) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_MEDIA_TYPE",
                            "IMAGE requires image/* mime type.");
                }
                break;
            case VIDEO:
                if (!mimeType.startsWith("video/")) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_MEDIA_TYPE",
                            "VIDEO requires video/* mime type.");
                }
                break;
            case DOCUMENT:
                if (!(mimeType.startsWith("text/") || ALLOWED_DOCUMENT_MIME_TYPES.contains(mimeType))) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_MEDIA_TYPE",
                            "DOCUMENT type is not supported for this mime type.");
                }
                break;
            default:
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Unsupported messageType for send-media.");
        }
    }

    public record MessageMediaResult(byte[] content, String mimeType, String filename) {}

    private record MediaMeta(String mediaId, String mimeType, String filename) {}
}

