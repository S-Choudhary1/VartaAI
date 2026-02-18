package tech.vartaai.whatsappcrm.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "api_key", length = 255)
    private String apiKey;

    @Column(name = "phone_number_id", length = 50)
    private String phoneNumberId;

    @Column(name = "waba_id", length = 50)
    private String wabaId;

    @Column(name = "access_token", length = 500) // Access tokens can be long
    private String accessToken;

    /**
     * Default language for messages sent on behalf of this client.
     * Example values: en_US, hi_IN, es_ES, etc.
     */
    @Column(name = "language", length = 32)
    private String language;

    @Column(name = "auto_reply_enabled", nullable = false)
    private boolean autoReplyEnabled = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (language == null || language.isBlank()) {
            // Default client language is English
            language = "en_US";
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
    public String getWabaId() { return wabaId; }
    public void setWabaId(String wabaId) { this.wabaId = wabaId; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public boolean isAutoReplyEnabled() { return autoReplyEnabled; }
    public void setAutoReplyEnabled(boolean autoReplyEnabled) { this.autoReplyEnabled = autoReplyEnabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

