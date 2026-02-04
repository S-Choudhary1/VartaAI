package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize webhook payload", e);
        }
    }

    @Transactional
    public void processIncoming(Map<String, Object> payload) {
        persistEvent("META", "incoming", payload);

        try {
            JsonNode root = objectMapper.valueToTree(payload);
            if (root.has("entry")) {
                for (JsonNode entry : root.get("entry")) {
                    if (entry.has("changes")) {
                        for (JsonNode change : entry.get("changes")) {
                            JsonNode value = change.get("value");
                            if (value != null && value.has("messages")) {
                                String phoneId = value.path("metadata").path("phone_number_id").asText();
                                Client client = clientRepository.findByPhoneNumberId(phoneId).orElse(null);

                                if (client == null) {
                                    continue;
                                }

                                for (JsonNode msgNode : value.get("messages")) {
                                    String from = msgNode.get("from").asText();
                                    String msgId = msgNode.get("id").asText();

                                    Contact contact = contactRepository.findByPhoneAndClient_Id(from, client.getId())
                                            .orElseGet(() -> {
                                                Contact newContact = new Contact();
                                                newContact.setClient(client);
                                                newContact.setPhone(from);
                                                newContact.setName(from);
                                                return contactRepository.save(newContact);
                                            });

                                    Message message = new Message();
                                    message.setClient(client);
                                    message.setContactId(contact.getId().toString());
                                    message.setDirection(Message.Direction.INCOMING);
                                    message.setStatus(Message.Status.DELIVERED);
                                    message.setProvider("META");
                                    message.setProviderMessageId(msgId);
                                    message.setPayloadJson(msgNode.toString());

                                    messageRepository.save(message);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}



