package tech.vartaai.whatsappcrm.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public class TemplateResponse {
    
    private UUID id;
    private String name;
    private String providerTemplateId;
    private String type;
    private Map<String, Object> content;
    private String languageCode;
    private String interactionType;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private boolean active;

    public TemplateResponse() {
    }

    public TemplateResponse(UUID id, String name, String providerTemplateId, String type,
                           Map<String, Object> content, String languageCode, String interactionType,
                           UUID createdBy, OffsetDateTime createdAt, boolean active) {
        this.id = id;
        this.name = name;
        this.providerTemplateId = providerTemplateId;
        this.type = type;
        this.content = content;
        this.languageCode = languageCode;
        this.interactionType = interactionType;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.active = active;
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProviderTemplateId() { return providerTemplateId; }
    public void setProviderTemplateId(String providerTemplateId) { this.providerTemplateId = providerTemplateId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, Object> getContent() { return content; }
    public void setContent(Map<String, Object> content) { this.content = content; }
    public String getLanguageCode() { return languageCode; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }
    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String interactionType) { this.interactionType = interactionType; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
