package tech.vartaai.whatsappcrm.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auto_reply_rules")
@Data
public class AutoReplyRule {

    public enum RuleType {
        TEXT_EXACT,
        BUTTON_PAYLOAD,
        INTERACTIVE_REPLY_ID,
        DEFAULT
    }

    public enum ResponseType {
        TEXT,
        TEMPLATE
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 40)
    private RuleType ruleType;

    @Column(name = "match_value", length = 300)
    private String matchValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", nullable = false, length = 20)
    private ResponseType responseType;

    @Column(name = "response_text", columnDefinition = "text")
    private String responseText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private Template template;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
