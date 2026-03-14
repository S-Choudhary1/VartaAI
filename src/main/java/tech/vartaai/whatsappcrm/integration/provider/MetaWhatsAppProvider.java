package tech.vartaai.whatsappcrm.integration.provider;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.MultipartBodyBuilder;
import tech.vartaai.whatsappcrm.exception.MediaApiException;
import tech.vartaai.whatsappcrm.config.WhatsAppProperties;
import tech.vartaai.whatsappcrm.dto.MetaTemplateListResponse;
import tech.vartaai.whatsappcrm.dto.MetaTemplateResponse;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.entity.Template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MetaWhatsAppProvider implements WhatsAppProvider {

    private final WebClient webClient;

    public MetaWhatsAppProvider(WhatsAppProperties props) {
        this.webClient = WebClient.builder()
                .baseUrl(props.getApiBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getAccessToken())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer ->
                                configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                        .build())
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
    public SendResponse sendMedia(Client client, String phone, Message.MessageType messageType, byte[] fileBytes,
                                  String filename, String mimeType, String caption) {
        String phoneNumberId = client.getPhoneNumberId();
        String accessToken = client.getAccessToken();

        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("messaging_product", "whatsapp");
            builder.part("file", new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }).contentType(MediaType.parseMediaType(mimeType));

            JsonNode uploadResponse = webClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/" + phoneNumberId + "/media").build())
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(builder.build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String mediaId = uploadResponse != null ? uploadResponse.path("id").asText(null) : null;
            if (mediaId == null || mediaId.isBlank()) {
                throw new RuntimeException("Failed to upload media to provider");
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", phone);
            payload.put("type", messageType.name().toLowerCase());

            Map<String, Object> mediaPayload = new HashMap<>();
            mediaPayload.put("id", mediaId);
            if (caption != null && !caption.isBlank()) {
                mediaPayload.put("caption", caption);
            }
            if (messageType == Message.MessageType.DOCUMENT && filename != null && !filename.isBlank()) {
                mediaPayload.put("filename", filename);
            }
            payload.put(messageType.name().toLowerCase(), mediaPayload);

            JsonNode sendResponse = webClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/" + phoneNumberId + "/messages").build())
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String providerMessageId = sendResponse != null
                    && sendResponse.has("messages")
                    && sendResponse.get("messages").isArray()
                    && sendResponse.get("messages").size() > 0
                    ? sendResponse.get("messages").get(0).path("id").asText(null)
                    : null;

            SendResponse response = new SendResponse(providerMessageId, "SENT");
            response.setMediaId(mediaId);
            response.setMimeType(mimeType);
            response.setFilename(filename);
            return response;
        } catch (Exception ex) {
            log.error("WA_MEDIA_SEND_FAILED to={} type={} err={}", phone, messageType, ex.getMessage());
            throw new RuntimeException("Failed to send media message", ex);
        }
    }

    @Override
    public MediaDownload downloadMedia(Client client, String mediaId, String fallbackMimeType, String fallbackFilename) {
        String accessToken = client.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new MediaApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_MEDIA_FETCH_FAILED",
                    "Provider credentials are missing for this tenant.");
        }

        try {
            JsonNode mediaMeta = webClient.get()
                    .uri("/" + mediaId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (mediaMeta == null) {
                throw new MediaApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND",
                        "Media is unavailable for this message.");
            }

            String mediaUrl = mediaMeta.path("url").asText(null);
            if (mediaUrl == null || mediaUrl.isBlank()) {
                throw new MediaApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND",
                        "Media is unavailable for this message.");
            }

            String mimeType = firstNonBlank(
                    mediaMeta.path("mime_type").asText(null),
                    fallbackMimeType,
                    MediaType.APPLICATION_OCTET_STREAM_VALUE
            );
            String filename = firstNonBlank(
                    mediaMeta.path("filename").asText(null),
                    fallbackFilename,
                    mediaId + extensionFromMime(mimeType)
            );

            byte[] bytes;
            try {
                bytes = webClient.get()
                        .uri(mediaUrl)
                        .header("Authorization", "Bearer " + accessToken)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block();
            } catch (WebClientResponseException.NotFound ex) {
                throw new MediaApiException(HttpStatus.GONE, "MEDIA_GONE",
                        "Provider URL expired and media is not recoverable.");
            } catch (WebClientResponseException.Gone ex) {
                throw new MediaApiException(HttpStatus.GONE, "MEDIA_GONE",
                        "Provider URL expired and media is not recoverable.");
            }

            if (bytes == null || bytes.length == 0) {
                throw new MediaApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND",
                        "Media is unavailable for this message.");
            }

            return new MediaDownload(bytes, mimeType, filename);
        } catch (MediaApiException ex) {
            throw ex;
        } catch (WebClientResponseException.NotFound ex) {
            throw new MediaApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND",
                    "Media is unavailable for this message.");
        } catch (WebClientResponseException.Gone ex) {
            throw new MediaApiException(HttpStatus.GONE, "MEDIA_GONE",
                    "Provider URL expired and media is not recoverable.");
        } catch (WebClientResponseException ex) {
            throw new MediaApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_MEDIA_FETCH_FAILED",
                    "Failed to fetch media from provider.");
        } catch (Exception ex) {
            throw new MediaApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_MEDIA_FETCH_FAILED",
                    "Failed to fetch media from provider.");
        }
    }

    @Override
    public MetaTemplateResponse createTemplate(Client client, Map<String, Object> payload) {
        String wabaId = client.getWabaId();
        String accessToken = client.getAccessToken();
        if (wabaId == null || wabaId.isBlank()) {
            throw new RuntimeException("Client WABA ID is missing");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("Client access token is missing");
        }
        try {
            JsonNode response = webClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/" + wabaId + "/message_templates").build())
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("Meta template create returned empty response");
            }
            return new MetaTemplateResponse(
                    response.path("id").asText(null),
                    payload.get("name") != null ? payload.get("name").toString() : null,
                    response.path("status").asText(null),
                    response.path("category").asText(null),
                    payload.get("language") != null ? payload.get("language").toString() : null,
                    null,
                    response.path("rejection_reason").asText(null),
                    response.path("specific_rejection_reason").asText(null),
                    null,
                    response
            );
        } catch (Exception e) {
            log.error("Failed to create template on Meta: {}", e.getMessage());
            throw new RuntimeException("Failed to create template on Meta", e);
        }
    }

    @Override
    public MetaTemplateListResponse getTemplates(Client client, Map<String, String> filters) {
        String wabaId = client.getWabaId();
        String accessToken = client.getAccessToken();

        if (wabaId == null || wabaId.isBlank()) {
            throw new RuntimeException("Client WABA ID is missing");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("Client access token is missing");
        }

        try {
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/" + wabaId + "/message_templates")
                                .queryParam("fields", "id,name,components,language,status,category,quality_score,rejection_reason,specific_rejection_reason")
                                .queryParam("limit", 255);
                        if (filters != null) {
                            filters.forEach((key, value) -> {
                                if (value != null && !value.isBlank()) {
                                    builder.queryParam(key, value);
                                }
                            });
                        }
                        return builder.build();
                    })
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data") || !response.get("data").isArray()) {
                return new MetaTemplateListResponse(Collections.emptyList(), null);
            }

            List<MetaTemplateResponse> templates = new ArrayList<>();
            for (JsonNode item : response.get("data")) {
                if(item.path("status") != null && "APPROVED".equalsIgnoreCase(item.path("status").asText())) {
                    templates.add(new MetaTemplateResponse(
                            item.path("id").asText(null),
                            item.path("name").asText(null),
                            item.path("status").asText(null),
                            item.path("category").asText(null),
                            item.path("language").asText(null),
                            item.path("quality_score").asText(null),
                            item.path("rejection_reason").asText(null),
                            item.path("specific_rejection_reason").asText(null),
                            item.path("components"),
                            item
                    ));
                }
            }
            JsonNode paging = response.path("paging").isMissingNode() ? null : response.path("paging");
            return new MetaTemplateListResponse(templates, paging);
        } catch (Exception e) {
            log.error("Failed to fetch templates from Meta: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch templates from Meta", e);
        }
    }

    @Override
    public void handleWebhook(JsonNode payload) {
        // Log webhook payload for debugging
        log.debug("Received webhook: {}", payload);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String extensionFromMime(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "";
        }
        if (mimeType.contains("jpeg")) return ".jpg";
        if (mimeType.contains("png")) return ".png";
        if (mimeType.contains("gif")) return ".gif";
        if (mimeType.contains("webp")) return ".webp";
        if (mimeType.contains("pdf")) return ".pdf";
        if (mimeType.contains("mp4")) return ".mp4";
        if (mimeType.contains("mpeg")) return ".mp3";
        if (mimeType.contains("ogg")) return ".ogg";
        return "";
    }
}


