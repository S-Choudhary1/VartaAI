package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.vartaai.whatsappcrm.dto.SendMessageRequest;
import tech.vartaai.whatsappcrm.dto.message.*;
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

import static tech.vartaai.whatsappcrm.util.StringUtils.firstNonBlank;
import static tech.vartaai.whatsappcrm.util.StringUtils.isBlank;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class MessageService {

    private static final EnumSet<Message.MessageType> MEDIA_MESSAGE_TYPES = EnumSet.of(
            Message.MessageType.IMAGE,
            Message.MessageType.VIDEO,
            Message.MessageType.AUDIO,
            Message.MessageType.DOCUMENT,
            Message.MessageType.STICKER
    );

    /** Types allowed via the file-upload endpoint (POST /send-media) */
    private static final EnumSet<Message.MessageType> UPLOAD_MEDIA_TYPES = EnumSet.of(
            Message.MessageType.IMAGE,
            Message.MessageType.VIDEO,
            Message.MessageType.AUDIO,
            Message.MessageType.DOCUMENT,
            Message.MessageType.STICKER
    );

    /** All types that can be sent via POST /send (JSON body) */
    private static final EnumSet<Message.MessageType> SENDABLE_TYPES = EnumSet.of(
            Message.MessageType.TEXT,
            Message.MessageType.TEMPLATE,
            Message.MessageType.IMAGE,
            Message.MessageType.VIDEO,
            Message.MessageType.AUDIO,
            Message.MessageType.DOCUMENT,
            Message.MessageType.STICKER,
            Message.MessageType.LOCATION,
            Message.MessageType.CONTACTS,
            Message.MessageType.INTERACTIVE,
            Message.MessageType.REACTION
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

    // ═══════════════════════════════════════════════════════════════
    //  UNIFIED SEND (JSON body — all message types)
    // ═══════════════════════════════════════════════════════════════

    public SendResponse sendMessage(SendMessageRequest request, UUID clientId) {
        log.info("Processing send request type={} to={} clientId={}", request.getMessageType(), request.getTo(), clientId);

        if (request.getMessageType() == null || !SENDABLE_TYPES.contains(request.getMessageType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Unsupported messageType: " + request.getMessageType());
        }

        validatePayload(request);

        Client client = loadClient(clientId);

        // Reactions don't create a new contact thread — they target an existing message
        Contact contact = null;
        if (request.getMessageType() != Message.MessageType.REACTION) {
            contact = findOrCreateContact(request.getTo(), clientId, client);
        }

        // Resolve template if needed
        Template template = null;
        if (request.getMessageType() == Message.MessageType.TEMPLATE) {
            template = resolveTemplate(request.getTemplate(), clientId);
        }

        // Build message entity
        Message message = new Message();
        message.setClient(client);
        message.setContactId(contact != null ? contact.getId() : null);
        message.setDirection(Message.Direction.OUTGOING);
        message.setProvider("META");
        message.setCampaignId(request.getCampaignId());
        message.setStatus(Message.Status.QUEUED);
        message.setMessageType(request.getMessageType());

        // Context (reply-to)
        if (request.getContext() != null && request.getContext().getMessageId() != null) {
            message.setContextMessageId(request.getContext().getMessageId());
        }

        // Store payload
        message.setPayloadJson(buildPayloadJson(request, template));

        try {
            SendResponse resp = provider.sendMessage(client, request, template);

            message.setStatus(Message.Status.SENT);
            message.setProviderMessageId(resp != null ? resp.getProviderMessageId() : null);
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
            message.setError(e.getMessage());
            messageRepository.save(message);
            log.error("Failed to send {} to {}: {}", request.getMessageType(), request.getTo(), e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PROVIDER_ERROR",
                    "Failed to send message: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  MEDIA UPLOAD SEND (multipart file upload)
    // ═══════════════════════════════════════════════════════════════

    public SendResponse sendMediaMessage(UUID clientId, String to, Message.MessageType messageType,
                                         MultipartFile file, String caption) {
        if (messageType == null || !UPLOAD_MEDIA_TYPES.contains(messageType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "messageType must be IMAGE, VIDEO, AUDIO, DOCUMENT, or STICKER.");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "file is required.");
        }

        String mimeType = firstNonBlank(file.getContentType(), "");
        validateMediaMimeType(messageType, mimeType);

        Client client = loadClient(clientId);
        Contact contact = findOrCreateContact(to, clientId, client);

        Message message = new Message();
        message.setClient(client);
        message.setContactId(contact.getId());
        message.setDirection(Message.Direction.OUTGOING);
        message.setProvider("META");
        message.setStatus(Message.Status.QUEUED);
        message.setMessageType(messageType);

        try {
            SendResponse providerResp = provider.sendMediaUpload(
                    client, to, messageType,
                    file.getBytes(),
                    firstNonBlank(file.getOriginalFilename(), "upload.bin"),
                    mimeType, caption
            );

            message.setProviderMessageId(providerResp.getProviderMessageId());
            message.setStatus(Message.Status.SENT);

            // Build payload JSON for storage
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
            message.setError(e.getMessage());
            messageRepository.save(message);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PROVIDER_ERROR",
                    "Failed to send media message.");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  MEDIA DOWNLOAD
    // ═══════════════════════════════════════════════════════════════

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

        Client client = loadClient(clientId);

        MediaMeta mediaMeta = resolveMediaMeta(message);
        if (mediaMeta.mediaId() == null || mediaMeta.mediaId().isBlank()) {
            throw new MediaApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND",
                    "Media is unavailable for this message.");
        }

        MediaDownload download = provider.downloadMedia(client, mediaMeta.mediaId(), mediaMeta.mimeType(), mediaMeta.filename());
        return new MessageMediaResult(download.getContent(), download.getMimeType(), download.getFilename());
    }

    // ═══════════════════════════════════════════════════════════════
    //  VALIDATION
    // ═══════════════════════════════════════════════════════════════

    private void validatePayload(SendMessageRequest request) {
        switch (request.getMessageType()) {
            case TEXT -> {
                if (request.getText() == null || request.getText().getBody() == null || request.getText().getBody().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "text.body is required for TEXT messages.");
                }
            }
            case TEMPLATE -> {
                if (request.getTemplate() == null || request.getTemplate().getTemplateId() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "template.templateId is required for TEMPLATE messages.");
                }
            }
            case IMAGE -> {
                if (request.getImage() == null || (isBlank(request.getImage().getLink()) && isBlank(request.getImage().getMediaId()))) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "image.link or image.mediaId is required.");
                }
            }
            case VIDEO -> {
                if (request.getVideo() == null || (isBlank(request.getVideo().getLink()) && isBlank(request.getVideo().getMediaId()))) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "video.link or video.mediaId is required.");
                }
            }
            case AUDIO -> {
                if (request.getAudio() == null || (isBlank(request.getAudio().getLink()) && isBlank(request.getAudio().getMediaId()))) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "audio.link or audio.mediaId is required.");
                }
            }
            case DOCUMENT -> {
                if (request.getDocument() == null || (isBlank(request.getDocument().getLink()) && isBlank(request.getDocument().getMediaId()))) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "document.link or document.mediaId is required.");
                }
            }
            case STICKER -> {
                if (request.getSticker() == null || (isBlank(request.getSticker().getLink()) && isBlank(request.getSticker().getMediaId()))) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "sticker.link or sticker.mediaId is required.");
                }
            }
            case LOCATION -> {
                if (request.getLocation() == null || request.getLocation().getLatitude() == null || request.getLocation().getLongitude() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "location.latitude and location.longitude are required.");
                }
            }
            case CONTACTS -> {
                if (request.getContacts() == null || request.getContacts().isEmpty()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "contacts list is required and must not be empty.");
                }
                for (ContactCardPayload c : request.getContacts()) {
                    if (c.getName() == null || isBlank(c.getName().getFormattedName())) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Each contact must have name.formattedName.");
                    }
                }
            }
            case INTERACTIVE -> {
                if (request.getInteractive() == null || isBlank(request.getInteractive().getType())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "interactive.type is required.");
                }
                if (request.getInteractive().getBody() == null || isBlank(request.getInteractive().getBody().getText())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "interactive.body.text is required.");
                }
                if (request.getInteractive().getAction() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "interactive.action is required.");
                }
            }
            case REACTION -> {
                if (request.getReaction() == null || isBlank(request.getReaction().getMessageId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "reaction.messageId is required.");
                }
                if (request.getReaction().getEmoji() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "reaction.emoji is required (empty string to remove).");
                }
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Unsupported messageType: " + request.getMessageType());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    private Client loadClient(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND", "Client not found."));
        if (isBlank(client.getPhoneNumberId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CLIENT_NOT_CONFIGURED",
                    "Client phoneNumberId is not configured.");
        }
        if (isBlank(client.getAccessToken())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CLIENT_NOT_CONFIGURED",
                    "Client accessToken is not configured.");
        }
        return client;
    }

    private Contact findOrCreateContact(String phone, UUID clientId, Client client) {
        return contactRepository.findByPhoneAndClient_Id(phone, clientId)
                .orElseGet(() -> {
                    Contact newContact = new Contact();
                    newContact.setClient(client);
                    newContact.setPhone(phone);
                    newContact.setName(phone);
                    return contactRepository.save(newContact);
                });
    }

    private Template resolveTemplate(TemplatePayload templatePayload, UUID clientId) {
        Template template = templateRepository.findById(templatePayload.getTemplateId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "Template not found."));

        if (!template.getClient().getId().equals(clientId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Template not found or access denied.");
        }
        if (template.getStatus() != Template.TemplateStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_NOT_APPROVED", "Template is not approved.");
        }
        return template;
    }

    private String buildPayloadJson(SendMessageRequest request, Template template) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", request.getMessageType().name().toLowerCase());

            switch (request.getMessageType()) {
                case TEXT -> payload.put("text", Map.of("body", request.getText().getBody()));
                case TEMPLATE -> {
                    Map<String, String> vars = request.getTemplate().getVariables() != null
                            ? request.getTemplate().getVariables() : Map.of();
                    payload.put("templateId", request.getTemplate().getTemplateId());
                    payload.put("templateName", template != null ? template.getName() : null);
                    payload.put("variables", vars);
                    payload.put("body", template != null ? template.getContentJson() : null);
                }
                case IMAGE -> payload.put("image", request.getImage());
                case VIDEO -> payload.put("video", request.getVideo());
                case AUDIO -> payload.put("audio", request.getAudio());
                case DOCUMENT -> payload.put("document", request.getDocument());
                case STICKER -> payload.put("sticker", request.getSticker());
                case LOCATION -> payload.put("location", request.getLocation());
                case CONTACTS -> payload.put("contacts", request.getContacts());
                case INTERACTIVE -> payload.put("interactive", request.getInteractive());
                case REACTION -> payload.put("reaction", request.getReaction());
            }

            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize payload JSON: {}", e.getMessage());
            return "{}";
        }
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
                    typeNode.path("mediaId").asText(null),
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
            case AUDIO:
                if (!mimeType.startsWith("audio/")) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_MEDIA_TYPE",
                            "AUDIO requires audio/* mime type.");
                }
                break;
            case DOCUMENT:
                if (!(mimeType.startsWith("text/") || ALLOWED_DOCUMENT_MIME_TYPES.contains(mimeType))) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_MEDIA_TYPE",
                            "DOCUMENT type is not supported for this mime type.");
                }
                break;
            case STICKER:
                if (!mimeType.contains("webp")) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_MEDIA_TYPE",
                            "STICKER requires image/webp mime type.");
                }
                break;
            default:
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Unsupported messageType for media upload.");
        }
    }

    public record MessageMediaResult(byte[] content, String mimeType, String filename) {}
    private record MediaMeta(String mediaId, String mimeType, String filename) {}
}
