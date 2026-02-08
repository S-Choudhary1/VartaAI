package tech.vartaai.whatsappcrm.integration.provider;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tech.vartaai.whatsappcrm.config.WhatsAppProperties;
import tech.vartaai.whatsappcrm.entity.Template;

import java.util.Map;

@Service
@Slf4j
public class MetaWhatsAppProvider implements WhatsAppProvider {

    private final WebClient webClient;
    private final WhatsAppProperties props;

    public MetaWhatsAppProvider(WhatsAppProperties props) {
        this.props = props;
        this.webClient = WebClient.builder()
                .baseUrl(props.getApiBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getAccessToken())
                .build();
    }

    @Override
    public SendResponse sendTemplate(tech.vartaai.whatsappcrm.entity.Client client, String phone, Template template, Map<String, String> variables) {
        try {
            String phoneNumberId = client.getPhoneNumberId();
            String accessToken = client.getAccessToken();

            // Minimal payload for template send; real implementation should map content JSON properly
            JsonNode response = webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/" + phoneNumberId + "/messages").build())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                    "messaging_product", "whatsapp",
                    "to", phone,
                    "type", "template",
                    "template", Map.of(
                        "name", template.getName(),
                        "language", Map.of("code", "en_US")
                    )
                ))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            log.info("Message response {}" , response);
            String providerMessageId = response != null && response.has("messages") && response.get("messages").isArray() &&
                response.get("messages").size() > 0
                ? response.get("messages").get(0).get("id").asText()
                : null;
            return new SendResponse(providerMessageId, "SENT");
        } catch (Exception e) {
            log.error("error " , e);
            return  new SendResponse("providerMessageId", "FAILED");
        }
    }


    @Override
    public SendResponse sendText(tech.vartaai.whatsappcrm.entity.Client client, String phone, String text) {
        String phoneNumberId = client.getPhoneNumberId();
        String accessToken = client.getAccessToken();

        try {
            JsonNode response = webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/" + phoneNumberId + "/messages").build())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                    "messaging_product", "whatsapp",
                    "to", phone,
                    "type", "text",
                    "text", Map.of("body", text)
                ))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            log.info("Message response {}" , response);

            String providerMessageId = response != null && response.has("messages") && response.get("messages").isArray() &&
                response.get("messages").size() > 0
                ? response.get("messages").get(0).get("id").asText()
                : null;
            return new SendResponse(providerMessageId, "SENT");
        } catch (Exception e) {
            log.error("Failed to send text message to {}: {}", phone, e.getMessage());
            return new SendResponse(null, "FAILED");
        }
    }

    @Override
    public void handleWebhook(JsonNode payload) {
        // Log webhook payload for debugging
        log.debug("Received webhook: {}", payload);
    }
}


