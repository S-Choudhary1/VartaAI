package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.vartaai.whatsappcrm.exception.ApiException;
import tech.vartaai.whatsappcrm.dto.MetaTemplateListResponse;
import tech.vartaai.whatsappcrm.dto.MetaTemplateResponse;
import tech.vartaai.whatsappcrm.dto.TemplateButtonRequest;
import tech.vartaai.whatsappcrm.dto.TemplateComponentRequest;
import tech.vartaai.whatsappcrm.dto.TemplateResponse;
import tech.vartaai.whatsappcrm.dto.TemplateV2Request;
import tech.vartaai.whatsappcrm.entity.Template;
import tech.vartaai.whatsappcrm.integration.provider.WhatsAppProvider;
import tech.vartaai.whatsappcrm.repository.ClientRepository;
import tech.vartaai.whatsappcrm.repository.TemplateRepository;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import tech.vartaai.whatsappcrm.entity.Client;

import static tech.vartaai.whatsappcrm.util.StringUtils.firstNonBlank;

@Service
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final ClientRepository clientRepository;
    private final WhatsAppProvider whatsAppProvider;
    private final ObjectMapper objectMapper;

    public TemplateService(TemplateRepository templateRepository,
                           ClientRepository clientRepository,
                           WhatsAppProvider whatsAppProvider,
                           ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.clientRepository = clientRepository;
        this.whatsAppProvider = whatsAppProvider;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════════
    //  CRUD (component-based, formerly "v2")
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public TemplateResponse createTemplate(TemplateV2Request request, UUID createdBy, UUID clientId) {
        validateTemplateRequest(request);

        Template template = new Template();
        Client client = new Client();
        client.setId(clientId);
        template.setClient(client);
        applyFields(template, request);
        template.setCreatedBy(createdBy);

        Template saved = templateRepository.save(template);
        return toResponse(saved);
    }

    @Transactional
    public TemplateResponse updateTemplate(UUID id, TemplateV2Request request, UUID clientId) {
        validateTemplateRequest(request);

        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "Template not found."));
        if (!template.getClient().getId().equals(clientId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "Template not found.");
        }

        applyFields(template, request);
        Template saved = templateRepository.save(template);
        return toResponse(saved);
    }

    public TemplateResponse getTemplateById(UUID id, UUID clientId) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "Template not found."));
        if (!template.getClient().getId().equals(clientId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "Template not found.");
        }
        return toResponse(template);
    }

    public List<TemplateResponse> getAllTemplates(UUID clientId) {
        return templateRepository.findByClient_Id(clientId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Page<TemplateResponse> getAllTemplates(UUID clientId, Pageable pageable) {
        return templateRepository.findByClient_Id(clientId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void deleteTemplate(UUID id, UUID clientId) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "Template not found."));
        if (!template.getClient().getId().equals(clientId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "Template not found.");
        }
        templateRepository.deleteById(id);
    }

    // ═══════════════════════════════════════════════════════════════
    //  META API OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public TemplateResponse createTemplateOnMeta(TemplateV2Request request, UUID clientId, UUID createdBy) {
        validateTemplateRequest(request);
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND", "Client not found."));

        Map<String, Object> providerPayload = buildMetaTemplateCreatePayload(request);
        MetaTemplateResponse providerResponse = whatsAppProvider.createTemplate(client, providerPayload);

        Template template = templateRepository
                .findByClient_IdAndProviderTemplateId(clientId, providerResponse.getId())
                .orElseGet(() -> {
                    Template t = new Template();
                    t.setClient(client);
                    t.setProviderTemplateId(providerResponse.getId());
                    t.setCreatedBy(createdBy);
                    t.setActive(true);
                    return t;
                });

        template.setName(request.getName());
        template.setLanguageCode(request.getLanguageCode());
        template.setCategory(Template.TemplateCategory.fromValue(providerResponse.getCategory()));
        template.setStatus(Template.TemplateStatus.fromValue(providerResponse.getStatus()));
        template.setType(detectTemplateType(request.getComponents()));
        template.setContentJson(extractBodyTextFromComponents(request.getComponents()));
        template.setLastSyncedAt(java.time.OffsetDateTime.now());

        try {
            template.setComponentsJson(objectMapper.writeValueAsString(request.getComponents()));
            template.setRawTemplateJson(objectMapper.writeValueAsString(providerPayload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize template payload", e);
        }

        Template saved = templateRepository.save(template);
        return toResponse(saved);
    }

    @Transactional
    public MetaTemplateListResponse getTemplatesFromMeta(UUID clientId, Map<String, String> filters) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND", "Client not found."));
        MetaTemplateListResponse metaResponse = whatsAppProvider.getTemplates(client, filters);
        if (metaResponse.getData() != null) {
            for (MetaTemplateResponse metaTemplate : metaResponse.getData()) {
                upsertTemplateFromMeta(client, metaTemplate);
            }
        }
        return metaResponse;
    }

    public MetaTemplateListResponse getApprovedTemplatesFromInternal(UUID clientId) {
        List<MetaTemplateResponse> data = templateRepository
                .findByClient_IdAndStatus(clientId, Template.TemplateStatus.APPROVED)
                .stream()
                .map(this::toMetaTemplateResponseFromInternal)
                .collect(Collectors.toList());
        return new MetaTemplateListResponse(data, null);
    }

    public String renderTemplate(Template template, Map<String, String> variables) {
        try {
            TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
            Map<String, Object> content = objectMapper.readValue(template.getContentJson(), typeRef);
            String text = (String) content.getOrDefault("text", "");
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return text;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to render template", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  INTERNAL HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void applyFields(Template template, TemplateV2Request request) {
        template.setName(request.getName());
        template.setLanguageCode(request.getLanguageCode());
        template.setCategory(Template.TemplateCategory.fromValue(request.getCategory()));
        template.setType(detectTemplateType(request.getComponents()));
        template.setStatus(Template.TemplateStatus.DRAFT);

        template.setContentJson(extractBodyTextFromComponents(request.getComponents()));

        try {
            template.setComponentsJson(objectMapper.writeValueAsString(request.getComponents()));

            // Store example/sample values
            Map<String, List<String>> exampleValues = new HashMap<>();
            for (TemplateComponentRequest component : request.getComponents()) {
                if (component.getSampleValues() != null && !component.getSampleValues().isEmpty()) {
                    exampleValues.put(component.getType().toUpperCase(), component.getSampleValues());
                }
            }
            if (!exampleValues.isEmpty()) {
                template.setExampleValuesJson(objectMapper.writeValueAsString(exampleValues));
            }

            Map<String, Object> rawPayload = new HashMap<>();
            rawPayload.put("name", request.getName());
            rawPayload.put("category", request.getCategory());
            rawPayload.put("language", request.getLanguageCode());
            rawPayload.put("components", request.getComponents());
            template.setRawTemplateJson(objectMapper.writeValueAsString(rawPayload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize template payload", e);
        }
    }

    private Template.TemplateType detectTemplateType(List<TemplateComponentRequest> components) {
        boolean hasButtons = components.stream().anyMatch(c -> "BUTTONS".equalsIgnoreCase(c.getType()));
        boolean hasHeaderMedia = components.stream().anyMatch(c ->
                "HEADER".equalsIgnoreCase(c.getType()) &&
                        c.getFormat() != null &&
                        !"TEXT".equalsIgnoreCase(c.getFormat()));

        if (hasButtons) return Template.TemplateType.INTERACTIVE;
        if (hasHeaderMedia) return Template.TemplateType.MEDIA;
        return Template.TemplateType.TEXT;
    }

    private void validateTemplateRequest(TemplateV2Request request) {
        if (!request.getName().matches("^[a-z0-9_]+$")) {
            throw new RuntimeException("Template name must contain lowercase letters, numbers, and underscores only");
        }

        Template.TemplateCategory category = Template.TemplateCategory.fromValue(request.getCategory());
        if (category == Template.TemplateCategory.UNKNOWN) {
            throw new RuntimeException("Category must be one of MARKETING, UTILITY, AUTHENTICATION");
        }

        if (!request.getLanguageCode().matches("^[a-z]{2}_[A-Z]{2}$")) {
            throw new RuntimeException("Language code must be in format en_US");
        }

        long bodyCount = request.getComponents().stream()
                .filter(c -> "BODY".equalsIgnoreCase(c.getType()))
                .count();
        if (bodyCount != 1) {
            throw new RuntimeException("Exactly one BODY component is required");
        }

        for (TemplateComponentRequest component : request.getComponents()) {
            String type = component.getType() == null ? "" : component.getType().toUpperCase();
            switch (type) {
                case "HEADER":
                    validateHeader(component);
                    break;
                case "BODY":
                    if (component.getText() == null || component.getText().isBlank()) {
                        throw new RuntimeException("BODY text is required");
                    }
                    break;
                case "FOOTER":
                    if (component.getText() != null && component.getText().length() > 60) {
                        throw new RuntimeException("FOOTER text must be <= 60 characters");
                    }
                    break;
                case "BUTTONS":
                    validateButtons(component.getButtons());
                    break;
                default:
                    throw new RuntimeException("Unsupported component type: " + component.getType());
            }
        }
    }

    private void validateHeader(TemplateComponentRequest component) {
        String format = component.getFormat() == null ? "" : component.getFormat().toUpperCase();
        if (format.isBlank()) {
            throw new RuntimeException("HEADER format is required");
        }
        if ("TEXT".equals(format)) {
            if (component.getText() == null || component.getText().isBlank()) {
                throw new RuntimeException("HEADER text is required when format is TEXT");
            }
            if (component.getText().length() > 60) {
                throw new RuntimeException("HEADER text must be <= 60 characters");
            }
            return;
        }
        boolean valid = "IMAGE".equals(format) || "VIDEO".equals(format)
                || "DOCUMENT".equals(format) || "LOCATION".equals(format);
        if (!valid) {
            throw new RuntimeException("Invalid HEADER format: " + component.getFormat());
        }
    }

    private void validateButtons(List<TemplateButtonRequest> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            throw new RuntimeException("BUTTONS component requires at least one button");
        }
        if (buttons.size() > 10) {
            throw new RuntimeException("BUTTONS component supports maximum 10 buttons");
        }
        for (TemplateButtonRequest button : buttons) {
            String type = button.getType() == null ? "" : button.getType().toUpperCase();
            switch (type) {
                case "QUICK_REPLY":
                case "COPY_CODE":
                case "OTP":
                case "VOICE_CALL":
                    if (button.getText() == null || button.getText().isBlank()) {
                        throw new RuntimeException(type + " button requires text");
                    }
                    break;
                case "URL":
                    if (button.getText() == null || button.getText().isBlank()) {
                        throw new RuntimeException("URL button requires text");
                    }
                    if (button.getUrl() == null || button.getUrl().isBlank()) {
                        throw new RuntimeException("URL button requires url");
                    }
                    break;
                case "PHONE_NUMBER":
                    if (button.getText() == null || button.getText().isBlank()) {
                        throw new RuntimeException("PHONE_NUMBER button requires text");
                    }
                    if (button.getPhoneNumber() == null || button.getPhoneNumber().isBlank()) {
                        throw new RuntimeException("PHONE_NUMBER button requires phoneNumber");
                    }
                    break;
                default:
                    throw new RuntimeException("Unsupported button type: " + button.getType());
            }
        }
    }

    private TemplateResponse toResponse(Template template) {
        TemplateResponse response = new TemplateResponse();
        response.setId(template.getId());
        response.setName(template.getName());
        response.setProviderTemplateId(template.getProviderTemplateId());
        response.setType(template.getType() != null ? template.getType().name() : null);
        response.setContent(template.getContentJson());
        response.setLanguageCode(template.getLanguageCode());
        response.setInteractionType(template.getInteractionType());
        response.setCategory(template.getCategory() != null ? template.getCategory().name() : null);
        response.setStatus(template.getStatus() != null ? template.getStatus().name() : null);
        response.setQualityRating(template.getQualityRating() != null ? template.getQualityRating().name() : null);
        response.setAllowCategoryChange(template.getAllowCategoryChange());
        response.setComponentsJson(template.getComponentsJson());
        response.setExampleValuesJson(template.getExampleValuesJson());
        response.setRawTemplateJson(template.getRawTemplateJson());
        response.setLastSyncedAt(template.getLastSyncedAt());
        response.setCreatedBy(template.getCreatedBy());
        response.setCreatedAt(template.getCreatedAt());
        response.setActive(template.isActive());
        return response;
    }

    private void upsertTemplateFromMeta(Client client, MetaTemplateResponse metaTemplate) {
        if (metaTemplate == null || metaTemplate.getId() == null) return;

        Template template = templateRepository
                .findByClient_IdAndProviderTemplateId(client.getId(), metaTemplate.getId())
                .orElseGet(() -> {
                    Template t = new Template();
                    t.setClient(client);
                    t.setProviderTemplateId(metaTemplate.getId());
                    t.setActive(true);
                    return t;
                });

        template.setName(firstNonBlank(metaTemplate.getName(), template.getName(), "meta_" + metaTemplate.getId()));
        template.setLanguageCode(firstNonBlank(metaTemplate.getLanguage(), template.getLanguageCode()));
        template.setCategory(Template.TemplateCategory.fromValue(metaTemplate.getCategory()));
        template.setStatus(Template.TemplateStatus.fromValue(metaTemplate.getStatus()));
        template.setQualityRating(Template.QualityRating.fromValue(metaTemplate.getQualityScore()));
        template.setType(detectTemplateTypeFromMetaComponents(metaTemplate.getComponents()));
        template.setContentJson(extractBodyTextFromMetaComponents(metaTemplate.getComponents()));

        try {
            if (metaTemplate.getComponents() != null && !metaTemplate.getComponents().isNull()) {
                template.setComponentsJson(objectMapper.writeValueAsString(metaTemplate.getComponents()));
            }
            if (metaTemplate.getRaw() != null && !metaTemplate.getRaw().isNull()) {
                template.setRawTemplateJson(objectMapper.writeValueAsString(metaTemplate.getRaw()));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Meta template payload", e);
        }
        template.setLastSyncedAt(java.time.OffsetDateTime.now());
        templateRepository.save(template);
    }

    private MetaTemplateResponse toMetaTemplateResponseFromInternal(Template template) {
        JsonNode componentsNode = null;
        JsonNode rawNode = null;
        try {
            if (template.getComponentsJson() != null && !template.getComponentsJson().isBlank()) {
                componentsNode = objectMapper.readTree(template.getComponentsJson());
            }
            if (template.getRawTemplateJson() != null && !template.getRawTemplateJson().isBlank()) {
                rawNode = objectMapper.readTree(template.getRawTemplateJson());
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse internal template JSON", e);
        }

        return new MetaTemplateResponse(
                template.getId().toString(),
                template.getName(),
                template.getStatus() != null ? template.getStatus().name() : null,
                template.getCategory() != null ? template.getCategory().name() : null,
                template.getLanguageCode(),
                template.getQualityRating() != null ? template.getQualityRating().name() : null,
                null, null,
                componentsNode,
                rawNode
        );
    }

    private Template.TemplateType detectTemplateTypeFromMetaComponents(JsonNode components) {
        if (components == null || !components.isArray()) return Template.TemplateType.CUSTOM;
        boolean hasButtons = false;
        boolean hasHeaderMedia = false;
        for (JsonNode component : components) {
            String type = component.path("type").asText("");
            if ("BUTTONS".equalsIgnoreCase(type)) hasButtons = true;
            if ("HEADER".equalsIgnoreCase(type)) {
                String format = component.path("format").asText("");
                if (!format.isBlank() && !"TEXT".equalsIgnoreCase(format)) hasHeaderMedia = true;
            }
        }
        if (hasButtons) return Template.TemplateType.INTERACTIVE;
        if (hasHeaderMedia) return Template.TemplateType.MEDIA;
        return Template.TemplateType.TEXT;
    }

    private String extractBodyTextFromMetaComponents(JsonNode components) {
        if (components == null || !components.isArray()) return "";
        for (JsonNode component : components) {
            if ("BODY".equalsIgnoreCase(component.path("type").asText(""))) {
                return firstNonBlank(component.path("text").asText(null), "");
            }
        }
        return "";
    }

    private String extractBodyTextFromComponents(List<TemplateComponentRequest> components) {
        if (components == null) return "";
        for (TemplateComponentRequest component : components) {
            if ("BODY".equalsIgnoreCase(component.getType())) {
                return firstNonBlank(component.getText(), "");
            }
        }
        return "";
    }

    private Map<String, Object> buildMetaTemplateCreatePayload(TemplateV2Request request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", request.getName());
        payload.put("language", request.getLanguageCode());
        payload.put("category", request.getCategory());
        payload.put("components", toMetaComponents(request.getComponents()));
        return payload;
    }

    private List<Map<String, Object>> toMetaComponents(List<TemplateComponentRequest> components) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (TemplateComponentRequest component : components) {
            String type = component.getType() == null ? "" : component.getType().toUpperCase();
            Map<String, Object> node = new HashMap<>();
            node.put("type", type);

            switch (type) {
                case "HEADER":
                    if (component.getFormat() != null && !component.getFormat().isBlank()) {
                        node.put("format", component.getFormat().toUpperCase());
                    }
                    if (component.getText() != null && !component.getText().isBlank()) {
                        node.put("text", component.getText());
                    }
                    if (component.getSampleValues() != null && !component.getSampleValues().isEmpty()) {
                        Map<String, Object> example = new HashMap<>();
                        if ("TEXT".equalsIgnoreCase(component.getFormat())) {
                            example.put("header_text", component.getSampleValues());
                        } else if ("IMAGE".equalsIgnoreCase(component.getFormat())
                                || "VIDEO".equalsIgnoreCase(component.getFormat())
                                || "DOCUMENT".equalsIgnoreCase(component.getFormat())) {
                            example.put("header_handle", component.getSampleValues());
                        }
                        if (!example.isEmpty()) node.put("example", example);
                    }
                    break;
                case "BODY":
                    node.put("text", component.getText());
                    if (component.getSampleValues() != null && !component.getSampleValues().isEmpty()) {
                        Map<String, Object> example = new HashMap<>();
                        example.put("body_text", List.of(component.getSampleValues()));
                        node.put("example", example);
                    }
                    break;
                case "FOOTER":
                    if (component.getText() != null && !component.getText().isBlank()) {
                        node.put("text", component.getText());
                    }
                    break;
                case "BUTTONS":
                    List<Map<String, Object>> buttons = new ArrayList<>();
                    if (component.getButtons() != null) {
                        for (TemplateButtonRequest b : component.getButtons()) {
                            Map<String, Object> bn = new HashMap<>();
                            if (b.getType() != null) bn.put("type", b.getType().toUpperCase());
                            if (b.getText() != null && !b.getText().isBlank()) bn.put("text", b.getText());
                            if (b.getUrl() != null && !b.getUrl().isBlank()) bn.put("url", b.getUrl());
                            if (b.getPhoneNumber() != null && !b.getPhoneNumber().isBlank()) bn.put("phone_number", b.getPhoneNumber());
                            buttons.add(bn);
                        }
                    }
                    node.put("buttons", buttons);
                    break;
            }
            result.add(node);
        }
        return result;
    }
}
