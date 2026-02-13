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
import tech.vartaai.whatsappcrm.entity.WebhookEvent;
import tech.vartaai.whatsappcrm.repository.ClientRepository;
import tech.vartaai.whatsappcrm.repository.ContactRepository;
import tech.vartaai.whatsappcrm.repository.MessageRepository;
import tech.vartaai.whatsappcrm.repository.WebhookEventRepository;

import java.util.Map;

@Service
@Slf4j
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;
    private final ClientRepository clientRepository;
    private final ContactRepository contactRepository;
    private final MessageRepository messageRepository;

    public WebhookService(WebhookEventRepository webhookEventRepository, 
                          ObjectMapper objectMapper,
                          ClientRepository clientRepository,
                          ContactRepository contactRepository,
                          MessageRepository messageRepository) {
        this.webhookEventRepository = webhookEventRepository;
        this.objectMapper = objectMapper;
        this.clientRepository = clientRepository;
        this.contactRepository = contactRepository;
        this.messageRepository = messageRepository;
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

                if (!"messages".equals(field)) {
                    log.info("WA_SKIP unsupported field={}", field);
                    continue;
                }

                String phoneNumberId = value.path("metadata")
                        .path("phone_number_id").asText();

                Client client = clientRepository
                        .findByPhoneNumberId(phoneNumberId)
                        .orElse(null);

                if (client == null) {
                    log.warn("WA_CLIENT_NOT_FOUND phoneNumberId={}", phoneNumberId);
                    continue;
                }

                log.info("WA_CLIENT_RESOLVED clientId={}", client.getId());

                if (value.has("messages")) {
                    handleIncomingMessages(value, client);
                }

                if (value.has("statuses")) {
                    handleStatusReceipts(value);
                }
            }
        }
    }

    private void handleIncomingMessages(JsonNode value, Client client) {

        JsonNode contactsNode = value.path("contacts");

        String contactName = null;
        String waId = null;

        if (contactsNode.isArray() && contactsNode.size() > 0) {
            contactName = contactsNode.get(0)
                    .path("profile")
                    .path("name").asText(null);

            waId = contactsNode.get(0)
                    .path("wa_id").asText(null);
        }

        for (JsonNode msg : value.get("messages")) {

            String msgId = msg.path("id").asText();
            String from = msg.path("from").asText();
            String type = msg.path("type").asText();
            String ts = msg.path("timestamp").asText();
            String contextId = msg.path("context").path("id").asText(null);

            log.info("WA_INCOMING msgId={} from={} type={} contextId={}", msgId, from, type, contextId);

            // If this is a reply to an outgoing message (has context.id),
            // attach the user's response to the existing message row instead
            // of creating a separate INCOMING row.
            if (contextId != null && !contextId.isBlank()) {
                Message original = messageRepository
                        .findByProviderMessageId(contextId)
                        .orElse(null);

                if (original != null) {
                    String responseJson = buildUserResponseJson(type, msg);
                    original.setResponseJson(responseJson);
                    messageRepository.save(original);
                    log.info("WA_RESPONSE_ATTACHED originalMsgId={} replyMsgId={}", contextId, msgId);
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
            m.setContactId(contact.getId().toString());
            m.setDirection(Message.Direction.INCOMING);
            m.setProvider("META");
            m.setProviderMessageId(msgId);
            m.setStatus(Message.Status.DELIVERED);
            m.setPayloadJson(msg.toString());
            m.setResponseJson(buildUserResponseJson(type, msg));

            messageRepository.save(m);

            log.info("WA_MESSAGE_SAVED msgId={} dbId={}", msgId, m.getId());

            // optional auto reply
//            autoReplyLogic(m, contact, client);
        }
    }

    private void handleStatusReceipts(JsonNode value) {

        for (JsonNode status : value.get("statuses")) {

            String msgId = status.path("id").asText();
            String state = status.path("status").asText();
            String recipient = status.path("recipient_id").asText();
            String ts = status.path("timestamp").asText();

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
                    return objectMapper.writeValueAsString(Map.of(
                            "type", "text",
                            "text", body
                    ));
                }
                case "button": {
                    JsonNode button = msg.path("button");
                    String text = button.path("text").asText(null);
                    String payload = button.path("payload").asText(null);
                    log.info("WA_BUTTON_REPLY msgId={} text={} payload={}",
                            msg.path("id").asText(), text, payload);
                    return objectMapper.writeValueAsString(Map.of(
                            "type", "button",
                            "text", text,
                            "payload", payload
                    ));
                }
                default:
                    // Generic fallback for other message types (image, interactive, etc.)
                    return objectMapper.writeValueAsString(Map.of(
                            "type", type,
                            "raw", msg
                    ));
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize user response for msgId={}", msg.path("id").asText(), e);
            return null;
        }
    }

}



