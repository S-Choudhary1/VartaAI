package tech.vartaai.whatsappcrm.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_alerts")
@Data
public class AccountAlert {

    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    public enum AlertCategory {
        ACCOUNT_UPDATE,
        ACCOUNT_REVIEW,
        PHONE_QUALITY,
        PHONE_NAME_UPDATE,
        MESSAGING_LIMIT,
        TEMPLATE_STATUS,
        SECURITY,
        BILLING,
        UNKNOWN
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private AlertCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private Severity severity;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "meta_event_field", length = 100)
    private String metaEventField;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
