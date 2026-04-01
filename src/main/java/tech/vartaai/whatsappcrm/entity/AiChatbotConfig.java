package tech.vartaai.whatsappcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_chatbot_config")
@Data
public class AiChatbotConfig {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    @JsonIgnore
    private Client client;

    @Column(name = "name", length = 255)
    private String name = "AI Assistant";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "provider", length = 30)
    private String provider;

    @Column(name = "model", length = 100)
    private String model = "claude-sonnet-4-20250514";

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "api_key", length = 500)
    private String apiKey;

    @Transient
    @JsonProperty("hasApiKey")
    public boolean getHasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Column(name = "system_prompt", columnDefinition = "TEXT", nullable = false)
    private String systemPrompt;

    @Column(name = "max_tokens")
    private int maxTokens = 1024;

    @Column(name = "temperature")
    private double temperature = 0.7;

    @Column(name = "fallback_message", columnDefinition = "TEXT")
    private String fallbackMessage = "I couldn't process your request. Type 'agent' to connect with a human.";

    @Column(name = "human_handoff_enabled")
    private boolean humanHandoffEnabled = true;

    @Column(name = "human_handoff_keyword", length = 50)
    private String humanHandoffKeyword = "agent";

    @Column(name = "max_conversation_turns")
    private int maxConversationTurns = 20;

    @Column(name = "conversation_timeout_hours")
    private int conversationTimeoutHours = 24;

    @Column(name = "max_messages_per_day_per_contact")
    private int maxMessagesPerDayPerContact = 100;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
