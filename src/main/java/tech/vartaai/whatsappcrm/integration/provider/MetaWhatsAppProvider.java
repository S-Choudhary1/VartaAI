package tech.vartaai.whatsappcrm.integration.provider;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tech.vartaai.whatsappcrm.config.WhatsAppProperties;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Template;

import java.util.ArrayList;
import java.util.List;
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
    public SendResponse sendTemplate(
            Client client,
            String phone,
            Template template,
            Map<String, String> variables) {

        try {

            String phoneNumberId = client.getPhoneNumberId();
            String accessToken = client.getAccessToken();

            log.info("WA_TEMPLATE_SEND start template={} to={}",
                    template.getName(), phone);

            // =========================
            // Build BODY parameters
            // =========================

            List<Map<String, Object>> bodyParams = new ArrayList<>();

            variables.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey()) // var1,var2,var3 order
                    .forEach(e -> bodyParams.add(
                            Map.of(
                                    "type", "text",
                                    "text", e.getValue()
                            )
                    ));

            List<Map<String, Object>> components = new ArrayList<>();

            if (!bodyParams.isEmpty()) {
                components.add(Map.of(
                        "type", "body",
                        "parameters", bodyParams
                ));
            }

            // =========================
            // Build payload
            // =========================

            String languageCode = template.getLanguageCode();
            if (languageCode == null || languageCode.isBlank()) {
                languageCode = client.getLanguage();
            }
            if (languageCode == null || languageCode.isBlank()) {
                languageCode = "en_US";
            }

            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "to", phone,
                    "type", "template",
                    "template", Map.of(
                            "name", template.getName(),
                            "language", Map.of(
                                    "code", languageCode
                            ),
                            "components", components
                    )
            );

            log.debug("WA_TEMPLATE_PAYLOAD {}", payload);

            // =========================
            // Send
            // =========================

            JsonNode response = webClient.post()
                    .uri("/" + phoneNumberId + "/messages")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            log.info("WA_TEMPLATE_RESPONSE {}", response);

            // =========================
            // Extract provider id
            // =========================

            String providerMessageId = null;

            if (response != null &&
                    response.has("messages") &&
                    response.get("messages").isArray() &&
                    response.get("messages").size() > 0) {

                providerMessageId =
                        response.get("messages")
                                .get(0)
                                .path("id")
                                .asText(null);
            }

            if (providerMessageId == null) {
                log.error("WA_TEMPLATE_NO_MSG_ID response={}", response);
                return new SendResponse(null, "FAILED");
            }

            log.info("WA_TEMPLATE_SENT providerMsgId={}", providerMessageId);

            return new SendResponse(providerMessageId, "SENT");
        } catch (Exception e) {
            log.error("error {}" , e.getMessage());
            throw new RuntimeException(e);
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
            log.error("error {}" , e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void handleWebhook(JsonNode payload) {
        // Log webhook payload for debugging
        log.debug("Received webhook: {}", payload);
    }
}


