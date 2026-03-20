package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Contact;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.entity.Template;
import tech.vartaai.whatsappcrm.entity.WebhookEvent;
import tech.vartaai.whatsappcrm.repository.ClientRepository;
import tech.vartaai.whatsappcrm.repository.ContactRepository;
import tech.vartaai.whatsappcrm.repository.MessageRepository;
import tech.vartaai.whatsappcrm.repository.TemplateRepository;
import tech.vartaai.whatsappcrm.repository.WebhookEventRepository;

import static tech.vartaai.whatsappcrm.util.StringUtils.firstNonBlank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;
    private final ClientRepository clientRepository;
    private final ContactRepository contactRepository;
    private final MessageRepository messageRepository;
    private final TemplateRepository templateRepository;
    private final FlowEngineService flowEngineService;

    public WebhookService(WebhookEventRepository webhookEventRepository,
                          ObjectMapper objectMapper,
                          ClientRepository clientRepository,
                          ContactRepository contactRepository,
                          MessageRepository messageRepository,
                          TemplateRepository templateRepository,
                          FlowEngineService flowEngineService) {
        this.webhookEventRepository = webhookEventRepository;
        this.objectMapper = objectMapper;
        this.clientRepository = clientRepository;
        this.contactRepository = contactRepository;
        this.messageRepository = messageRepository;
        this.templateRepository = templateRepository;
        this.flowEngineService = flowEngineService;
    }

    @Transactional
    public void persistEvent(String provider, String eventType, Object payload) {
        try {
            WebhookEvent event = new WebhookEvent();
            event.setProvider(provider);
            event.setEventType(eventType);
            event.setPayloadJson(objectMapper.writeValueAsString(payload));
            webhookEventRepository.save(event);
        } catch (Exception e) {
            log.error("Error in persist event ", e);
            throw new RuntimeException("Failed to serialize webhook payload", e);
        }
    }

    @Transactional
    public void processWebhook(Map<String, Object> payload) {
        persistEvent("META", "incoming", payload);
        log.info("WA_WEBHOOK_RECEIVED payloadSize={}", payload.size());

        JsonNode root = objectMapper.valueToTree(payload);

        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {

                String field = change.path("field").asText();
                JsonNode value = change.path("value");

                log.info("WA_CHANGE field={}", field);

                Client client = resolveClient(entry, value, field);

                if (client == null) {
                    log.warn("WA_CLIENT_NOT_FOUND field={} entryId={}", field, entry.path("id").asText(null));
                    continue;
                }

                log.info("WA_CLIENT_RESOLVED clientId={}", client.getId());

                switch (field) {
                    case "messages":
                        if (value.has("messages")) {
                            handleIncomingMessages(value, client);
                        }
                        if (value.has("statuses")) {
                            handleStatusReceipts(value);
                        }
                        break;
                    case "message_template_status_update":
                        handleTemplateStatusUpdate(value, client);
                        break;
                    case "message_template_quality_update":
                        handleTemplateQualityUpdate(value, client);
                        break;
                    case "message_template_components_update":
                        handleTemplateComponentsUpdate(value, client);
                        break;
                    default:
                        // Persisted via persistEvent already. Keep unknown fields non-fatal.
                        log.info("WA_SKIP unsupported field={}", field);
                        break;
                }
            }
        }
    }

    private void handleIncomingMessages(JsonNode value, Client client) {

        JsonNode contactsNode = value.path("contacts");

        String contactName = null;

        if (contactsNode.isArray() && contactsNode.size() > 0) {
            contactName = contactsNode.get(0)
                    .path("profile")
                    .path("name").asText(null);
        }

        for (JsonNode msg : value.get("messages")) {

            String msgId = msg.path("id").asText();
            String from = msg.path("from").asText();
            String type = msg.path("type").asText();
            String contextId = msg.path("context").path("id").asText(null);

            log.info("WA_INCOMING msgId={} from={} type={} contextId={}", msgId, from, type, contextId);

            // For reactions: attach to original message AND create standalone record
            if ("reaction".equals(type)) {
                String reactionTargetId = msg.path("reaction").path("message_id").asText(null);
                if (reactionTargetId != null && !reactionTargetId.isBlank()) {
                    Message original = messageRepository.findByProviderMessageId(reactionTargetId).orElse(null);
                    if (original != null) {
                        String responseJson = buildUserResponseJson(type, msg);
                        original.setResponseJson(responseJson);
                        messageRepository.save(original);
                        log.info("WA_REACTION_ATTACHED targetMsgId={} emoji={}", reactionTargetId,
                                msg.path("reaction").path("emoji").asText(""));
                    }
                }
                // Also create standalone INCOMING reaction record (falls through to normal create below)
            }

            // If this is a reply to an outgoing message (has context.id),
            // attach the user's response to the existing message row instead
            // of creating a separate INCOMING row.
            if (contextId != null && !contextId.isBlank() && !"reaction".equals(type)) {
                Message original = messageRepository
                        .findByProviderMessageId(contextId)
                        .orElse(null);

                if (original != null) {
                    String responseJson = buildUserResponseJson(type, msg);
                    original.setResponseJson(responseJson);
                    messageRepository.save(original);
                    log.info("WA_RESPONSE_ATTACHED originalMsgId={} replyMsgId={}", contextId, msgId);

                    // Also process flow engine for replies
                    Contact replyContact = findOrCreateContact(from, contactName, client);
                    log.info("FLOW_WEBHOOK_REPLY_TRIGGER contactId={} phone={} type={} contextMsgId={} replyMsgId={}",
                            replyContact.getId(), from, type, contextId, msgId);
                    try {
                        flowEngineService.processIncomingMessage(replyContact, client, responseJson, msgId);
                    } catch (Exception e) {
                        log.error("FLOW_ENGINE_REPLY_ERROR msgId={} contactId={} err={}",
                                msgId, replyContact.getId(), e.getMessage(), e);
                    }
                    continue;
                }
            }

            // ✅ idempotent check for stand‑alone incoming messages
            if (messageRepository.existsByProviderMessageId(msgId)) {
                log.warn("WA_DUPLICATE_MESSAGE msgId={}", msgId);
                continue;
            }

            Contact contact = findOrCreateContact(from, contactName, client);

            Message m = new Message();
            m.setClient(client);
            m.setContactId(contact.getId());
            m.setDirection(Message.Direction.INCOMING);
            m.setProvider("META");
            m.setProviderMessageId(msgId);
            m.setContextMessageId(contextId);
            m.setMessageType(Message.MessageType.fromValue(type));
            m.setStatus(Message.Status.DELIVERED);
            m.setPayloadJson(msg.toString());
            m.setResponseJson(buildUserResponseJson(type, msg));

            messageRepository.save(m);

            log.info("WA_MESSAGE_SAVED msgId={} dbId={}", msgId, m.getId());

            // Process flow engine for incoming messages (standalone, no context)
            log.info("FLOW_WEBHOOK_STANDALONE_TRIGGER contactId={} phone={} type={} msgId={}",
                    contact.getId(), from, type, msgId);
            try {
                String responseJsonForFlow = buildUserResponseJson(type, msg);
                flowEngineService.processIncomingMessage(contact, client, responseJsonForFlow, msgId);
            } catch (Exception e) {
                log.error("FLOW_ENGINE_WEBHOOK_ERROR msgId={} contactId={} err={}",
                        msgId, contact.getId(), e.getMessage(), e);
            }
        }
    }

    private void handleStatusReceipts(JsonNode value) {

        for (JsonNode status : value.get("statuses")) {

            String msgId = status.path("id").asText();
            String state = status.path("status").asText();
            String recipient = status.path("recipient_id").asText();

            log.info("WA_STATUS msgId={} status={} recipient={}",
                    msgId, state, recipient);

            Message msg = messageRepository
                    .findByProviderMessageId(msgId)
                    .orElse(null);

            if (msg == null) {
                log.warn("WA_STATUS_NO_MESSAGE msgId={}", msgId);
                continue;
            }

            switch (state) {
                case "sent":
                    msg.setStatus(Message.Status.SENT);
                    break;

                case "delivered":
                    msg.setStatus(Message.Status.DELIVERED);
                    break;

                case "read":
                    msg.setStatus(Message.Status.READ);
                    break;

                case "failed":
                    msg.setStatus(Message.Status.FAILED);
                    msg.setError(extractStatusError(status));
                    break;
                default:
                    msg.setStatus(Message.Status.UNKNOWN);
                    break;
            }

            messageRepository.save(msg);

            log.info("WA_STATUS_UPDATED msgId={} -> {}", msgId, state);
        }
    }


    private Contact findOrCreateContact(
            String phone,
            String name,
            Client client) {

        return contactRepository
                .findByPhoneAndClient_Id(phone, client.getId())
                .map(c -> {
                    if (name != null && !name.equals(c.getName())) {
                        c.setName(name);
                        contactRepository.save(c);
                        log.info("WA_CONTACT_NAME_UPDATED phone={}", phone);
                    }
                    return c;
                })
                .orElseGet(() -> {
                    Contact c = new Contact();
                    c.setPhone(phone);
                    c.setName(name != null ? name : phone);
                    c.setClient(client);
                    contactRepository.save(c);
                    log.info("WA_CONTACT_CREATED phone={}", phone);
                    return c;
                });
    }


    /**
     * Build a compact JSON representation of the user's response so that
     * downstream reporting (e.g. campaign CSV export) can easily read it.
     */
    private String buildUserResponseJson(String type, JsonNode msg) {
        try {
            switch (type) {
                case "text": {
                    String body = msg.path("text").path("body").asText(null);
                    log.info("WA_TEXT_BODY msgId={} text={}", msg.path("id").asText(), body);
                    Map<String, Object> normalized = new HashMap<>();
                    normalized.put("type", "text");
                    normalized.put("text", body);
                    return objectMapper.writeValueAsString(normalized);
                }
                case "button": {
                    JsonNode button = msg.path("button");
                    String text = button.path("text").asText(null);
                    String payload = button.path("payload").asText(null);
                    log.info("WA_BUTTON_REPLY msgId={} text={} payload={}",
                            msg.path("id").asText(), text, payload);
                    Map<String, Object> normalized = new HashMap<>();
                    normalized.put("type", "button");
                    normalized.put("text", text);
                    normalized.put("payload", payload);
                    return objectMapper.writeValueAsString(normalized);
                }
                case "interactive": {
                    JsonNode interactive = msg.path("interactive");
                    Map<String, Object> normalized = new HashMap<>();
                    normalized.put("type", "interactive");
                    normalized.put("interactiveType", interactive.path("type").asText(null));
                    if (interactive.has("button_reply")) {
                        JsonNode buttonReply = interactive.path("button_reply");
                        normalized.put("id", buttonReply.path("id").asText(null));
                        normalized.put("title", buttonReply.path("title").asText(null));
                    }
                    if (interactive.has("list_reply")) {
                        JsonNode listReply = interactive.path("list_reply");
                        normalized.put("id", listReply.path("id").asText(null));
                        normalized.put("title", listReply.path("title").asText(null));
                        normalized.put("description", listReply.path("description").asText(null));
                    }
                    return objectMapper.writeValueAsString(normalized);
                }
                case "reaction": {
                    JsonNode reaction = msg.path("reaction");
                    Map<String, Object> normalized = new HashMap<>();
                    normalized.put("type", "reaction");
                    normalized.put("emoji", reaction.path("emoji").asText(null));
                    normalized.put("messageId", reaction.path("message_id").asText(null));
                    return objectMapper.writeValueAsString(normalized);
                }
                case "image":
                case "video":
                case "audio":
                case "document":
                case "sticker": {
                    JsonNode media = msg.path(type);
                    Map<String, Object> normalized = new HashMap<>();
                    normalized.put("type", type);
                    normalized.put("id", media.path("id").asText(null));
                    normalized.put("mimeType", media.path("mime_type").asText(null));
                    normalized.put("sha256", media.path("sha256").asText(null));
                    normalized.put("caption", media.path("caption").asText(null));
                    normalized.put("filename", media.path("filename").asText(null));
                    return objectMapper.writeValueAsString(normalized);
                }
                case "location": {
                    JsonNode location = msg.path("location");
                    Map<String, Object> normalized = new HashMap<>();
                    normalized.put("type", "location");
                    normalized.put("latitude", location.path("latitude").asDouble());
                    normalized.put("longitude", location.path("longitude").asDouble());
                    normalized.put("name", location.path("name").asText(null));
                    normalized.put("address", location.path("address").asText(null));
                    return objectMapper.writeValueAsString(normalized);
                }
                case "contacts": {
                    JsonNode contacts = msg.path("contacts");
                    Map<String, Object> normalized = new HashMap<>();
                    normalized.put("type", "contacts");
                    normalized.put("contacts", contacts);
                    return objectMapper.writeValueAsString(normalized);
                }
                case "order": {
                    JsonNode order = msg.path("order");
                    Map<String, Object> normalized = new HashMap<>();
                    normalized.put("type", "order");
                    normalized.put("catalogId", order.path("catalog_id").asText(null));
                    normalized.put("text", order.path("text").asText(null));
                    normalized.put("productItems", order.path("product_items"));
                    return objectMapper.writeValueAsString(normalized);
                }
                case "request_welcome":
                case "ephemeral": {
                    Map<String, Object> normalized = new HashMap<>();
                    normalized.put("type", type);
                    normalized.put("raw", msg);
                    return objectMapper.writeValueAsString(normalized);
                }
                case "system": {
                    JsonNode system = msg.path("system");
                    Map<String, Object> normalized = new HashMap<>();
                    normalized.put("type", "system");
                    normalized.put("body", system.path("body").asText(null));
                    normalized.put("identity", system.path("identity").asText(null));
                    normalized.put("newWaId", system.path("new_wa_id").asText(null));
                    normalized.put("waId", system.path("wa_id").asText(null));
                    return objectMapper.writeValueAsString(normalized);
                }
                default:
                    // Generic fallback for other message types (image, interactive, etc.)
                    Map<String, Object> fallback = new HashMap<>();
                    fallback.put("type", type);
                    fallback.put("raw", msg);
                    return objectMapper.writeValueAsString(fallback);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize user response for msgId={}", msg.path("id").asText(), e);
            return null;
        }
    }

    private Client resolveClient(JsonNode entry, JsonNode value, String field) {
        if ("messages".equals(field)) {
            String phoneNumberId = value.path("metadata").path("phone_number_id").asText(null);
            if (phoneNumberId != null && !phoneNumberId.isBlank()) {
                return clientRepository.findByPhoneNumberId(phoneNumberId).orElse(null);
            }
        }
        String wabaId = entry.path("id").asText(null);
        if (wabaId != null && !wabaId.isBlank()) {
            return clientRepository.findByWabaId(wabaId).orElse(null);
        }
        return null;
    }

    private void handleTemplateStatusUpdate(JsonNode value, Client client) {
        Template template = resolveTemplateFromWebhook(value, client);
        if (template == null) {
            log.warn("WA_TEMPLATE_STATUS_NO_MATCH clientId={} value={}", client.getId(), value);
            return;
        }

        String nextStatus = firstNonBlank(
                value.path("event").asText(null),
                value.path("message_template_status").asText(null),
                value.path("status").asText(null)
        );
        if (nextStatus != null) {
            template.setStatus(Template.TemplateStatus.fromValue(nextStatus));
        }
        templateRepository.save(template);
        log.info("WA_TEMPLATE_STATUS_UPDATED template={} status={}", template.getName(), nextStatus);
    }

    private void handleTemplateQualityUpdate(JsonNode value, Client client) {
        Template template = resolveTemplateFromWebhook(value, client);
        if (template == null) {
            log.warn("WA_TEMPLATE_QUALITY_NO_MATCH clientId={} value={}", client.getId(), value);
            return;
        }

        String quality = firstNonBlank(
                value.path("new_quality_score").asText(null),
                value.path("quality_score").asText(null),
                value.path("event").asText(null)
        );
        if (quality != null) {
            template.setQualityRating(Template.QualityRating.fromValue(quality));
        }
        templateRepository.save(template);
        log.info("WA_TEMPLATE_QUALITY_UPDATED template={} quality={}", template.getName(), quality);
    }

    private void handleTemplateComponentsUpdate(JsonNode value, Client client) {
        Template template = resolveTemplateFromWebhook(value, client);
        if (template == null) {
            log.warn("WA_TEMPLATE_COMPONENTS_NO_MATCH clientId={} value={}", client.getId(), value);
            return;
        }

        try {
            template.setRawTemplateJson(objectMapper.writeValueAsString(value));
            if (value.has("components")) {
                template.setComponentsJson(objectMapper.writeValueAsString(value.get("components")));
            }
            templateRepository.save(template);
            log.info("WA_TEMPLATE_COMPONENTS_UPDATED template={}", template.getName());
        } catch (JsonProcessingException e) {
            log.error("Failed to save template components update", e);
        }
    }

    private Template resolveTemplateFromWebhook(JsonNode value, Client client) {
        String providerTemplateId = firstNonBlank(
                value.path("message_template_id").asText(null),
                value.path("template_id").asText(null),
                value.path("id").asText(null)
        );
        if (providerTemplateId != null) {
            return templateRepository
                    .findByClient_IdAndProviderTemplateId(client.getId(), providerTemplateId)
                    .orElse(null);
        }

        String templateName = firstNonBlank(
                value.path("message_template_name").asText(null),
                value.path("template_name").asText(null),
                value.path("name").asText(null)
        );
        if (templateName == null) {
            return null;
        }
        List<Template> templates = templateRepository.findByClient_IdAndName(client.getId(), templateName);
        return templates.isEmpty() ? null : templates.get(0);
    }

    private String extractStatusError(JsonNode status) {
        JsonNode errors = status.path("errors");
        if (!errors.isArray() || errors.isEmpty()) {
            return null;
        }
        JsonNode first = errors.get(0);
        String title = first.path("title").asText("");
        String message = first.path("message").asText("");
        String code = first.path("code").asText("");
        return (code + " " + title + " " + message).trim();
    }

}



