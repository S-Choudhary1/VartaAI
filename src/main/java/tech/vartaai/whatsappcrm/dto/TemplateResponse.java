package tech.vartaai.whatsappcrm.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class TemplateResponse {
    
    private UUID id;
    private String name;
    private String providerTemplateId;
    private String type;
    private String content;
    private String languageCode;
    private String interactionType;
    private String category;
    private String status;
    private String qualityRating;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private boolean active;

    public TemplateResponse() {
    }

    public TemplateResponse(UUID id, String name, String providerTemplateId, String type,
                           String content, String languageCode, String interactionType,
                           String category, String status, String qualityRating,
                           UUID createdBy, OffsetDateTime createdAt, boolean active) {
        this.id = id;
        this.name = name;
        this.providerTemplateId = providerTemplateId;
        this.type = type;
        this.content = content;
        this.languageCode = languageCode;
        this.interactionType = interactionType;
        this.category = category;
        this.status = status;
        this.qualityRating = qualityRating;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.active = active;
    }

}
