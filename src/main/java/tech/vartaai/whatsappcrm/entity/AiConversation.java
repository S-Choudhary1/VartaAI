package tech.vartaai.whatsappcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_conversations")
@Data
public class AiConversation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatbot_config_id", nullable = false)
    @JsonIgnore
    private AiChatbotConfig chatbotConfig;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "contact_phone", nullable = false, length = 50)
    private String contactPhone;

    @Column(name = "messages_json", columnDefinition = "TEXT")
    private String messagesJson;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "total_messages")
    private int totalMessages = 0;

    @Column(name = "total_tokens_used")
    private int totalTokensUsed = 0;

    @Column(name = "estimated_cost_usd")
    private double estimatedCostUsd = 0;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "last_message_at")
    private OffsetDateTime lastMessageAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (startedAt == null) startedAt = OffsetDateTime.now();
        if (lastMessageAt == null) lastMessageAt = OffsetDateTime.now();
    }
}
