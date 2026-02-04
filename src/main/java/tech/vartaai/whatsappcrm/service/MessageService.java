package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.vartaai.whatsappcrm.dto.SendMessageRequest;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Contact;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.entity.Template;
import tech.vartaai.whatsappcrm.integration.provider.SendResponse;
import tech.vartaai.whatsappcrm.integration.provider.WhatsAppProvider;
import tech.vartaai.whatsappcrm.repository.ClientRepository;
import tech.vartaai.whatsappcrm.repository.ContactRepository;
import tech.vartaai.whatsappcrm.repository.MessageRepository;
import tech.vartaai.whatsappcrm.repository.TemplateRepository;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class MessageService {

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
        
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

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

        try {
            if (request.getTemplateId() != null) {
                resp = sendTemplateMessage(client, request, message);
            } else {
                resp = sendTextMessage(client, request, message);
            }
            
            message.setStatus(Message.Status.SENT);
            if (resp != null) {
                message.setProviderMessageId(resp.getProviderMessageId());
            }
            messageRepository.save(message);
            return resp;

        } catch (Exception e) {
            log.error("Failed to send message to {}", request.getTo(), e);
            // We do NOT save the message to DB if sending failed, as requested.
            // Or we could save it as FAILED. User requirement said "don't write that messge in our database".
            // However, usually it's better to save as FAILED for audit. 
            // Strict interpretation: Do nothing.
            throw new RuntimeException("Failed to send message: " + e.getMessage());
        }
    }

    private SendResponse sendTemplateMessage(Client client, SendMessageRequest request, Message message) throws Exception {
        Template template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("Template not found"));
        
        if (!template.getClient().getId().equals(client.getId())) {
            throw new RuntimeException("Template not found or access denied");
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

        return provider.sendTemplate(client, request.getTo(), template, vars);
    }

    private SendResponse sendTextMessage(Client client, SendMessageRequest request, Message message) throws Exception {
        String textBody = request.getText() != null && !request.getText().isEmpty() ? request.getText() : "Hello";
        
        // Prepare payload log
        String payload = objectMapper.writeValueAsString(Map.of(
            "type", "text",
            "body", textBody
        ));
        message.setPayloadJson(payload);

        return provider.sendText(client, request.getTo(), textBody);
    }
}

