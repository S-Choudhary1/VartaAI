package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.vartaai.whatsappcrm.dto.TemplateRequest;
import tech.vartaai.whatsappcrm.dto.TemplateResponse;
import tech.vartaai.whatsappcrm.entity.Template;
import tech.vartaai.whatsappcrm.repository.TemplateRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import tech.vartaai.whatsappcrm.entity.Client;

@Service
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    public TemplateService(TemplateRepository templateRepository, ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
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
        template.setType(Template.TemplateType.valueOf(request.getType().toUpperCase()));
        template.setCreatedBy(createdBy);

        try {
            template.setContentJson(objectMapper.writeValueAsString(request.getContent()));
        } catch (JsonProcessingException e) {
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

    public List<TemplateResponse> getAllTemplates(UUID clientId) {
        return templateRepository.findByClient_Id(clientId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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
        template.setType(Template.TemplateType.valueOf(request.getType().toUpperCase()));

        try {
            template.setContentJson(objectMapper.writeValueAsString(request.getContent()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize template content", e);
        }

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

    private TemplateResponse toResponse(Template template) {
        try {
            TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
            Map<String, Object> content = objectMapper.readValue(template.getContentJson(), typeRef);
            
            return new TemplateResponse(
                    template.getId(),
                    template.getName(),
                    template.getProviderTemplateId(),
                    template.getType().name(),
                    content,
                    template.getCreatedBy(),
                    template.getCreatedAt(),
                    template.isActive()
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize template content", e);
        }
    }
}

