package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.vartaai.whatsappcrm.dto.MetaTemplateResponse;
import tech.vartaai.whatsappcrm.dto.TemplateButtonRequest;
import tech.vartaai.whatsappcrm.dto.TemplateComponentRequest;
import tech.vartaai.whatsappcrm.dto.TemplateRequest;
import tech.vartaai.whatsappcrm.dto.TemplateResponse;
import tech.vartaai.whatsappcrm.dto.TemplateV2Request;
import tech.vartaai.whatsappcrm.entity.Template;
import tech.vartaai.whatsappcrm.integration.provider.WhatsAppProvider;
import tech.vartaai.whatsappcrm.repository.ClientRepository;
import tech.vartaai.whatsappcrm.repository.TemplateRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import tech.vartaai.whatsappcrm.entity.Client;

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

    @Transactional
    public TemplateResponse createTemplate(TemplateRequest request, UUID createdBy, UUID clientId) {
        Template template = new Template();
        
        Client client = new Client();
        client.setId(clientId);
        template.setClient(client);

        template.setName(request.getName());
        template.setProviderTemplateId(request.getProviderTemplateId());
        template.setLanguageCode(request.getLanguageCode());
        template.setInteractionType(request.getInteractionType());
        template.setType(Template.TemplateType.fromValue(request.getType()));
        template.setCreatedBy(createdBy);

        try {
            template.setContentJson(request.getContent());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize template content", e);
        }

        Template saved = templateRepository.save(template);
        return toResponse(saved);
    }

    public TemplateResponse getTemplateById(UUID id, UUID clientId) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        
        if (!template.getClient().getId().equals(clientId)) {
             throw new RuntimeException("Template not found");
        }

        return toResponse(template);
    }

    public List<MetaTemplateResponse> getApprovedTemplatesFromMeta(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));
        return whatsAppProvider.getApprovedTemplates(client);
    }

    public List<TemplateResponse> getAllTemplates(UUID clientId) {
        return templateRepository.findByClient_Id(clientId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TemplateResponse createTemplateV2(TemplateV2Request request, UUID createdBy, UUID clientId) {
        validateTemplateV2Request(request);

        Template template = new Template();
        Client client = new Client();
        client.setId(clientId);
        template.setClient(client);
        applyV2Fields(template, request);
        template.setCreatedBy(createdBy);

        Template saved = templateRepository.save(template);
        return toResponse(saved);
    }

    @Transactional
    public TemplateResponse updateTemplate(UUID id, TemplateRequest request, UUID clientId) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        if (!template.getClient().getId().equals(clientId)) {
             throw new RuntimeException("Template not found");
        }

        template.setName(request.getName());
        template.setProviderTemplateId(request.getProviderTemplateId());
        template.setLanguageCode(request.getLanguageCode());
        template.setInteractionType(request.getInteractionType());
        template.setType(Template.TemplateType.fromValue(request.getType()));

        template.setContentJson(request.getContent());

        Template saved = templateRepository.save(template);
        return toResponse(saved);
    }

    @Transactional
    public TemplateResponse updateTemplateV2(UUID id, TemplateV2Request request, UUID clientId) {
        validateTemplateV2Request(request);

        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        if (!template.getClient().getId().equals(clientId)) {
            throw new RuntimeException("Template not found");
        }

        applyV2Fields(template, request);
        Template saved = templateRepository.save(template);
        return toResponse(saved);
    }

    @Transactional
    public void deleteTemplate(UUID id, UUID clientId) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        if (!template.getClient().getId().equals(clientId)) {
             throw new RuntimeException("Template not found");
        }
        templateRepository.deleteById(id);
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

    private void applyV2Fields(Template template, TemplateV2Request request) {
        template.setName(request.getName());
        template.setLanguageCode(request.getLanguageCode());
        template.setCategory(Template.TemplateCategory.fromValue(request.getCategory()));
        template.setType(detectTemplateType(request.getComponents()));
        template.setStatus(Template.TemplateStatus.DRAFT);

        TemplateComponentRequest body = request.getComponents().stream()
                .filter(c -> "BODY".equalsIgnoreCase(c.getType()))
                .findFirst()
                .orElse(null);
        template.setContentJson(body != null ? body.getText() : "");

        try {
            template.setComponentsJson(objectMapper.writeValueAsString(request.getComponents()));
            Map<String, Object> rawPayload = new HashMap<>();
            rawPayload.put("name", request.getName());
            rawPayload.put("category", request.getCategory());
            rawPayload.put("language", request.getLanguageCode());
            rawPayload.put("components", request.getComponents());
            template.setRawTemplateJson(objectMapper.writeValueAsString(rawPayload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize v2 template payload", e);
        }
    }

    private Template.TemplateType detectTemplateType(List<TemplateComponentRequest> components) {
        boolean hasButtons = components.stream().anyMatch(c -> "BUTTONS".equalsIgnoreCase(c.getType()));
        boolean hasHeaderMedia = components.stream().anyMatch(c ->
                "HEADER".equalsIgnoreCase(c.getType()) &&
                        c.getFormat() != null &&
                        !"TEXT".equalsIgnoreCase(c.getFormat()));

        if (hasButtons) {
            return Template.TemplateType.INTERACTIVE;
        }
        if (hasHeaderMedia) {
            return Template.TemplateType.MEDIA;
        }
        return Template.TemplateType.TEXT;
    }

    private void validateTemplateV2Request(TemplateV2Request request) {
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
        try {
            return new TemplateResponse(
                    template.getId(),
                    template.getName(),
                    template.getProviderTemplateId(),
                    template.getType().name(),
                    template.getContentJson(),
                    template.getLanguageCode(),
                    template.getInteractionType(),
                    template.getCategory() != null ? template.getCategory().name() : null,
                    template.getStatus() != null ? template.getStatus().name() : null,
                    template.getQualityRating() != null ? template.getQualityRating().name() : null,
                    template.getCreatedBy(),
                    template.getCreatedAt(),
                    template.isActive()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize template content", e);
        }
    }
}

