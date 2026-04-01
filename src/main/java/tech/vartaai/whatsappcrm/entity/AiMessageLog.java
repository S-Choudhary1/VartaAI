package tech.vartaai.whatsappcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_message_log")
@Data
public class AiMessageLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    @JsonIgnore
    private AiConversation conversation;

    @Column(name = "direction", length = 10)
    private String direction;

    @Column(name = "message_type", length = 50)
    private String messageType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "tokens_input")
    private int tokensInput = 0;

    @Column(name = "tokens_output")
    private int tokensOutput = 0;

    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Column(name = "latency_ms")
    private int latencyMs;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
