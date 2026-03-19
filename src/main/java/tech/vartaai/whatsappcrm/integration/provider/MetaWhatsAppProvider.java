package tech.vartaai.whatsappcrm.integration.provider;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.client.MultipartBodyBuilder;
import tech.vartaai.whatsappcrm.config.WhatsAppProperties;
import tech.vartaai.whatsappcrm.dto.MetaTemplateListResponse;
import tech.vartaai.whatsappcrm.dto.MetaTemplateResponse;
import tech.vartaai.whatsappcrm.dto.SendMessageRequest;
import tech.vartaai.whatsappcrm.dto.message.*;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.entity.Template;
import tech.vartaai.whatsappcrm.exception.MediaApiException;

import static tech.vartaai.whatsappcrm.util.StringUtils.firstNonBlank;

import java.util.*;

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

    // ═══════════════════════════════════════════════════════════════
    //  UNIFIED SEND
    // ═══════════════════════════════════════════════════════════════

    @Override
    public SendResponse sendMessage(Client client, SendMessageRequest request, Template template) {
        String phoneNumberId = client.getPhoneNumberId();
        String accessToken = client.getAccessToken();

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", request.getTo());

            // Context (reply-to)
            if (request.getContext() != null && request.getContext().getMessageId() != null
                    && !request.getContext().getMessageId().isBlank()) {
                payload.put("context", Map.of("message_id", request.getContext().getMessageId()));
            }

            String type = request.getMessageType().name().toLowerCase();
            payload.put("type", type);

            switch (request.getMessageType()) {
                case TEXT -> payload.put("text", buildTextPayload(request.getText()));
                case IMAGE -> payload.put("image", buildMediaPayload(request.getImage(), false));
                case VIDEO -> payload.put("video", buildMediaPayload(request.getVideo(), true));
                case AUDIO -> payload.put("audio", buildMediaPayload(request.getAudio(), false));
                case DOCUMENT -> payload.put("document", buildDocumentPayload(request.getDocument()));
                case STICKER -> payload.put("sticker", buildMediaPayload(request.getSticker(), false));
                case LOCATION -> payload.put("location", buildLocationPayload(request.getLocation()));
                case CONTACTS -> payload.put("contacts", buildContactsPayload(request.getContacts()));
                case INTERACTIVE -> payload.put("interactive", buildInteractivePayload(request.getInteractive()));
                case REACTION -> payload.put("reaction", buildReactionPayload(request.getReaction()));
                case TEMPLATE -> payload.put("template", buildTemplatePayload(template, request.getTemplate(), client));
                default -> throw new RuntimeException("Unsupported message type: " + request.getMessageType());
            }

            log.info("WA_SEND type={} to={}", type, request.getTo());
            log.debug("WA_SEND_PAYLOAD {}", payload);

            JsonNode response = webClient.post()
                    .uri("/" + phoneNumberId + "/messages")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String providerMessageId = extractProviderMessageId(response);
            if (providerMessageId == null) {
                log.error("WA_SEND_NO_MSG_ID response={}", response);
                return new SendResponse(null, "FAILED");
            }

            log.info("WA_SENT providerMsgId={}", providerMessageId);
            return new SendResponse(providerMessageId, "SENT");

        } catch (Exception e) {
            log.error("WA_SEND_FAILED type={} to={} err={}", request.getMessageType(), request.getTo(), e.getMessage());
            throw new RuntimeException("Failed to send " + request.getMessageType() + " message", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  MEDIA UPLOAD (for multipart file sends)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public SendResponse sendMediaUpload(Client client, String phone, Message.MessageType messageType,
                                         byte[] fileBytes, String filename, String mimeType, String caption) {
        String phoneNumberId = client.getPhoneNumberId();
        String accessToken = client.getAccessToken();

        try {
            // Step 1: Upload media
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("messaging_product", "whatsapp");
            builder.part("file", new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }).contentType(MediaType.parseMediaType(mimeType));

            JsonNode uploadResponse = webClient.post()
                    .uri("/" + phoneNumberId + "/media")
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

            // Step 2: Send message with uploaded media ID
            String typeKey = messageType.name().toLowerCase();

            Map<String, Object> mediaPayload = new LinkedHashMap<>();
            mediaPayload.put("id", mediaId);
            if (caption != null && !caption.isBlank()) {
                mediaPayload.put("caption", caption);
            }
            if (messageType == Message.MessageType.DOCUMENT && filename != null && !filename.isBlank()) {
                mediaPayload.put("filename", filename);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", phone);
            payload.put("type", typeKey);
            payload.put(typeKey, mediaPayload);

            JsonNode sendResponse = webClient.post()
                    .uri("/" + phoneNumberId + "/messages")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String providerMessageId = extractProviderMessageId(sendResponse);

            SendResponse response = new SendResponse(providerMessageId, "SENT");
            response.setMediaId(mediaId);
            response.setMimeType(mimeType);
            response.setFilename(filename);
            return response;
        } catch (Exception ex) {
            log.error("WA_MEDIA_UPLOAD_SEND_FAILED to={} type={} err={}", phone, messageType, ex.getMessage());
            throw new RuntimeException("Failed to send media message", ex);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PAYLOAD BUILDERS
    // ═══════════════════════════════════════════════════════════════

    private Map<String, Object> buildTextPayload(TextPayload text) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("body", text.getBody());
        if (Boolean.TRUE.equals(text.getPreviewUrl())) {
            map.put("preview_url", true);
        }
        return map;
    }

    /**
     * Build media payload for image/video/audio/sticker.
     * @param captionSupported true for video (also image/document handled separately)
     */
    private Map<String, Object> buildMediaPayload(MediaPayload media, boolean captionSupported) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (media.getMediaId() != null && !media.getMediaId().isBlank()) {
            map.put("id", media.getMediaId());
        } else if (media.getLink() != null && !media.getLink().isBlank()) {
            map.put("link", media.getLink());
        }
        if (media.getCaption() != null && !media.getCaption().isBlank()) {
            map.put("caption", media.getCaption());
        }
        return map;
    }

    private Map<String, Object> buildDocumentPayload(MediaPayload doc) {
        Map<String, Object> map = buildMediaPayload(doc, true);
        if (doc.getFilename() != null && !doc.getFilename().isBlank()) {
            map.put("filename", doc.getFilename());
        }
        return map;
    }

    private Map<String, Object> buildLocationPayload(LocationPayload loc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("latitude", loc.getLatitude());
        map.put("longitude", loc.getLongitude());
        if (loc.getName() != null && !loc.getName().isBlank()) {
            map.put("name", loc.getName());
        }
        if (loc.getAddress() != null && !loc.getAddress().isBlank()) {
            map.put("address", loc.getAddress());
        }
        return map;
    }

    private List<Map<String, Object>> buildContactsPayload(List<ContactCardPayload> contacts) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ContactCardPayload c : contacts) {
            Map<String, Object> contact = new LinkedHashMap<>();

            // Name (required)
            if (c.getName() != null) {
                Map<String, Object> name = new LinkedHashMap<>();
                if (c.getName().getFormattedName() != null) name.put("formatted_name", c.getName().getFormattedName());
                if (c.getName().getFirstName() != null) name.put("first_name", c.getName().getFirstName());
                if (c.getName().getLastName() != null) name.put("last_name", c.getName().getLastName());
                if (c.getName().getMiddleName() != null) name.put("middle_name", c.getName().getMiddleName());
                if (c.getName().getPrefix() != null) name.put("prefix", c.getName().getPrefix());
                if (c.getName().getSuffix() != null) name.put("suffix", c.getName().getSuffix());
                contact.put("name", name);
            }

            if (c.getPhones() != null && !c.getPhones().isEmpty()) {
                List<Map<String, String>> phones = new ArrayList<>();
                for (ContactCardPayload.Phone p : c.getPhones()) {
                    Map<String, String> phone = new LinkedHashMap<>();
                    if (p.getPhone() != null) phone.put("phone", p.getPhone());
                    if (p.getType() != null) phone.put("type", p.getType());
                    if (p.getWaId() != null) phone.put("wa_id", p.getWaId());
                    phones.add(phone);
                }
                contact.put("phones", phones);
            }

            if (c.getEmails() != null && !c.getEmails().isEmpty()) {
                List<Map<String, String>> emails = new ArrayList<>();
                for (ContactCardPayload.Email e : c.getEmails()) {
                    Map<String, String> email = new LinkedHashMap<>();
                    if (e.getEmail() != null) email.put("email", e.getEmail());
                    if (e.getType() != null) email.put("type", e.getType());
                    emails.add(email);
                }
                contact.put("emails", emails);
            }

            if (c.getAddresses() != null && !c.getAddresses().isEmpty()) {
                List<Map<String, String>> addresses = new ArrayList<>();
                for (ContactCardPayload.Address a : c.getAddresses()) {
                    Map<String, String> addr = new LinkedHashMap<>();
                    if (a.getStreet() != null) addr.put("street", a.getStreet());
                    if (a.getCity() != null) addr.put("city", a.getCity());
                    if (a.getState() != null) addr.put("state", a.getState());
                    if (a.getZip() != null) addr.put("zip", a.getZip());
                    if (a.getCountry() != null) addr.put("country", a.getCountry());
                    if (a.getCountryCode() != null) addr.put("country_code", a.getCountryCode());
                    if (a.getType() != null) addr.put("type", a.getType());
                    addresses.add(addr);
                }
                contact.put("addresses", addresses);
            }

            if (c.getOrg() != null) {
                Map<String, String> org = new LinkedHashMap<>();
                if (c.getOrg().getCompany() != null) org.put("company", c.getOrg().getCompany());
                if (c.getOrg().getDepartment() != null) org.put("department", c.getOrg().getDepartment());
                if (c.getOrg().getTitle() != null) org.put("title", c.getOrg().getTitle());
                contact.put("org", org);
            }

            if (c.getUrls() != null && !c.getUrls().isEmpty()) {
                List<Map<String, String>> urls = new ArrayList<>();
                for (ContactCardPayload.Url u : c.getUrls()) {
                    Map<String, String> url = new LinkedHashMap<>();
                    if (u.getUrl() != null) url.put("url", u.getUrl());
                    if (u.getType() != null) url.put("type", u.getType());
                    urls.add(url);
                }
                contact.put("urls", urls);
            }

            if (c.getBirthday() != null) {
                contact.put("birthday", c.getBirthday());
            }

            result.add(contact);
        }
        return result;
    }

    private Map<String, Object> buildInteractivePayload(InteractivePayload interactive) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", interactive.getType());

        // Header
        if (interactive.getHeader() != null) {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("type", interactive.getHeader().getType());
            if ("text".equalsIgnoreCase(interactive.getHeader().getType())) {
                header.put("text", interactive.getHeader().getText());
            } else if ("image".equalsIgnoreCase(interactive.getHeader().getType()) && interactive.getHeader().getImage() != null) {
                header.put("image", buildMediaRef(interactive.getHeader().getImage()));
            } else if ("video".equalsIgnoreCase(interactive.getHeader().getType()) && interactive.getHeader().getVideo() != null) {
                header.put("video", buildMediaRef(interactive.getHeader().getVideo()));
            } else if ("document".equalsIgnoreCase(interactive.getHeader().getType()) && interactive.getHeader().getDocument() != null) {
                header.put("document", buildMediaRef(interactive.getHeader().getDocument()));
            }
            map.put("header", header);
        }

        // Body
        if (interactive.getBody() != null) {
            map.put("body", Map.of("text", interactive.getBody().getText()));
        }

        // Footer
        if (interactive.getFooter() != null && interactive.getFooter().getText() != null) {
            map.put("footer", Map.of("text", interactive.getFooter().getText()));
        }

        // Action
        if (interactive.getAction() != null) {
            map.put("action", buildInteractiveAction(interactive));
        }

        return map;
    }

    private Map<String, Object> buildInteractiveAction(InteractivePayload interactive) {
        InteractivePayload.Action action = interactive.getAction();
        Map<String, Object> actionMap = new LinkedHashMap<>();

        switch (interactive.getType().toLowerCase()) {
            case "button" -> {
                // Reply buttons
                if (action.getButtons() != null) {
                    List<Map<String, Object>> buttons = new ArrayList<>();
                    for (InteractivePayload.ReplyButton rb : action.getButtons()) {
                        Map<String, Object> btn = new LinkedHashMap<>();
                        btn.put("type", rb.getType() != null ? rb.getType() : "reply");
                        if (rb.getReply() != null) {
                            btn.put("reply", Map.of(
                                    "id", rb.getReply().getId(),
                                    "title", rb.getReply().getTitle()
                            ));
                        }
                        buttons.add(btn);
                    }
                    actionMap.put("buttons", buttons);
                }
            }
            case "list" -> {
                if (action.getButton() != null) {
                    actionMap.put("button", action.getButton());
                }
                if (action.getSections() != null) {
                    List<Map<String, Object>> sections = new ArrayList<>();
                    for (InteractivePayload.Section section : action.getSections()) {
                        Map<String, Object> sec = new LinkedHashMap<>();
                        if (section.getTitle() != null) sec.put("title", section.getTitle());
                        if (section.getRows() != null) {
                            List<Map<String, String>> rows = new ArrayList<>();
                            for (InteractivePayload.Row row : section.getRows()) {
                                Map<String, String> r = new LinkedHashMap<>();
                                r.put("id", row.getId());
                                r.put("title", row.getTitle());
                                if (row.getDescription() != null) r.put("description", row.getDescription());
                                rows.add(r);
                            }
                            sec.put("rows", rows);
                        }
                        sections.add(sec);
                    }
                    actionMap.put("sections", sections);
                }
            }
            case "cta_url" -> {
                if (action.getName() != null) {
                    actionMap.put("name", action.getName());
                }
                if (action.getParameters() != null) {
                    Map<String, String> params = new LinkedHashMap<>();
                    if (action.getParameters().getDisplayText() != null) {
                        params.put("display_text", action.getParameters().getDisplayText());
                    }
                    if (action.getParameters().getUrl() != null) {
                        params.put("url", action.getParameters().getUrl());
                    }
                    actionMap.put("parameters", params);
                }
            }
            case "product" -> {
                if (action.getCatalogId() != null) actionMap.put("catalog_id", action.getCatalogId());
                if (action.getProductRetailerId() != null) actionMap.put("product_retailer_id", action.getProductRetailerId());
            }
            case "product_list" -> {
                if (action.getCatalogId() != null) actionMap.put("catalog_id", action.getCatalogId());
                if (action.getSections() != null) {
                    List<Map<String, Object>> sections = new ArrayList<>();
                    for (InteractivePayload.Section section : action.getSections()) {
                        Map<String, Object> sec = new LinkedHashMap<>();
                        if (section.getTitle() != null) sec.put("title", section.getTitle());
                        if (section.getProductItems() != null) {
                            List<Map<String, String>> items = new ArrayList<>();
                            for (InteractivePayload.ProductItem item : section.getProductItems()) {
                                items.add(Map.of("product_retailer_id", item.getProductRetailerId()));
                            }
                            sec.put("product_items", items);
                        }
                        sections.add(sec);
                    }
                    actionMap.put("sections", sections);
                }
            }
        }

        return actionMap;
    }

    private Map<String, String> buildMediaRef(InteractivePayload.MediaRef ref) {
        Map<String, String> map = new LinkedHashMap<>();
        if (ref.getId() != null && !ref.getId().isBlank()) {
            map.put("id", ref.getId());
        } else if (ref.getLink() != null && !ref.getLink().isBlank()) {
            map.put("link", ref.getLink());
        }
        return map;
    }

    private Map<String, Object> buildReactionPayload(ReactionPayload reaction) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("message_id", reaction.getMessageId());
        map.put("emoji", reaction.getEmoji());
        return map;
    }

    private Map<String, Object> buildTemplatePayload(Template template, TemplatePayload templatePayload,
                                                      Client client) {
        // Language resolution
        String languageCode = template.getLanguageCode();
        if (languageCode == null || languageCode.isBlank()) {
            languageCode = client.getLanguage();
        }
        if (languageCode == null || languageCode.isBlank()) {
            languageCode = "en_US";
        }

        // Build body parameters from variables
        List<Map<String, Object>> bodyParams = new ArrayList<>();
        Map<String, String> variables = templatePayload != null && templatePayload.getVariables() != null
                ? templatePayload.getVariables()
                : Map.of();

        variables.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> bodyParams.add(Map.of("type", "text", "text", e.getValue())));

        List<Map<String, Object>> components = new ArrayList<>();
        if (!bodyParams.isEmpty()) {
            components.add(Map.of("type", "body", "parameters", bodyParams));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", template.getName());
        result.put("language", Map.of("code", languageCode));
        result.put("components", components);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    //  MEDIA DOWNLOAD
    // ═══════════════════════════════════════════════════════════════

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
                    org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE
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
            } catch (WebClientResponseException.NotFound | WebClientResponseException.Gone ex) {
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
        } catch (WebClientResponseException.NotFound | WebClientResponseException.Gone ex) {
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

    // ═══════════════════════════════════════════════════════════════
    //  TEMPLATE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

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
                if ("APPROVED".equalsIgnoreCase(item.path("status").asText())) {
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
        log.debug("Received webhook: {}", payload);
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    private String extractProviderMessageId(JsonNode response) {
        if (response != null && response.has("messages")
                && response.get("messages").isArray()
                && !response.get("messages").isEmpty()) {
            return response.get("messages").get(0).path("id").asText(null);
        }
        return null;
    }

    private String extensionFromMime(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) return "";
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
