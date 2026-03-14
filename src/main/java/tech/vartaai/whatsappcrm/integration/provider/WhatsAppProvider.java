package tech.vartaai.whatsappcrm.integration.provider;

import com.fasterxml.jackson.databind.JsonNode;
import tech.vartaai.whatsappcrm.dto.MetaTemplateListResponse;
import tech.vartaai.whatsappcrm.dto.MetaTemplateResponse;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.entity.Template;

import java.util.Map;

public interface WhatsAppProvider {
    SendResponse sendTemplate(Client client, String phone, Template template, Map<String, String> variables);
    SendResponse sendText(Client client, String phone, String text);
    SendResponse sendMedia(Client client, String phone, Message.MessageType messageType, byte[] fileBytes,
                           String filename, String mimeType, String caption);
    MediaDownload downloadMedia(Client client, String mediaId, String fallbackMimeType, String fallbackFilename);
    MetaTemplateResponse createTemplate(Client client, Map<String, Object> payload);
    MetaTemplateListResponse getTemplates(Client client, Map<String, String> filters);
    void handleWebhook(JsonNode payload);
}


