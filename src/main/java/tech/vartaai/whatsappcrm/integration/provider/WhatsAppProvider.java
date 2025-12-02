package tech.vartaai.whatsappcrm.integration.provider;

import com.fasterxml.jackson.databind.JsonNode;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Template;

import java.util.Map;

public interface WhatsAppProvider {
    SendResponse sendTemplate(Client client, String phone, Template template, Map<String, String> variables);
    SendResponse sendText(Client client, String phone, String text);
    void handleWebhook(JsonNode payload);
}


