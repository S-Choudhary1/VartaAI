package tech.vartaai.whatsappcrm.integration.provider;

import com.fasterxml.jackson.databind.JsonNode;
import tech.vartaai.whatsappcrm.dto.MetaTemplateListResponse;
import tech.vartaai.whatsappcrm.dto.MetaTemplateResponse;
import tech.vartaai.whatsappcrm.dto.SendMessageRequest;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.entity.Template;

import java.util.Map;

public interface WhatsAppProvider {

    /**
     * Send any WhatsApp message type through a single unified method.
     * The implementation reads messageType from the request and builds
     * the appropriate Cloud API payload.
     *
     * @param client  tenant with Meta credentials
     * @param request unified message request
     * @param template resolved Template entity (only needed for TEMPLATE type, null otherwise)
     * @return send result with provider message ID
     */
    SendResponse sendMessage(Client client, SendMessageRequest request, Template template);

    /**
     * Send a media message by uploading raw bytes first, then sending.
     * Used for multipart file uploads (POST /send-media).
     */
    SendResponse sendMediaUpload(Client client, String phone, Message.MessageType messageType,
                                  byte[] fileBytes, String filename, String mimeType, String caption);

    MediaDownload downloadMedia(Client client, String mediaId, String fallbackMimeType, String fallbackFilename);

    MetaTemplateResponse createTemplate(Client client, Map<String, Object> payload);

    MetaTemplateListResponse getTemplates(Client client, Map<String, String> filters);

    void handleWebhook(JsonNode payload);
}
