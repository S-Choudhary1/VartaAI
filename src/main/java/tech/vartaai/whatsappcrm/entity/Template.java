package tech.vartaai.whatsappcrm.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "templates")
public class Template {

    public enum TemplateType { TEXT, MEDIA, INTERACTIVE }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "provider_template_id", length = 200)
    private String providerTemplateId;

    /**
     * Language code for this template, e.g. en_US, hi_IN.
     * If null, the client's default language (if any) will be used.
     */
    @Column(name = "language_code", length = 32)
    private String languageCode;

    /**
     * High-level interaction category for this template
     * (e.g. CHOICE, EXTERNAL_LINK). This is for app/UI logic
     * and does not affect how Meta stores the template.
     */
    @Column(name = "interaction_type", length = 50)
    private String interactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private TemplateType type;

    @Type(JsonBinaryType.class)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    private String contentJson;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProviderTemplateId() { return providerTemplateId; }
    public void setProviderTemplateId(String providerTemplateId) { this.providerTemplateId = providerTemplateId; }
    public String getLanguageCode() { return languageCode; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }
    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String interactionType) { this.interactionType = interactionType; }
    public TemplateType getType() { return type; }
    public void setType(TemplateType type) { this.type = type; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}


