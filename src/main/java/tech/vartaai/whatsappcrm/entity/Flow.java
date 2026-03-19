package tech.vartaai.whatsappcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "flows")
@Data
public class Flow {

    public enum FlowStatus { DRAFT, ACTIVE, PAUSED, ARCHIVED }
    public enum TriggerType { KEYWORD, CAMPAIGN, MANUAL }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    @JsonIgnore
    private Client client;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private FlowStatus status = FlowStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 50)
    private TriggerType triggerType;

    @Column(name = "trigger_keywords", columnDefinition = "TEXT")
    private String triggerKeywords;

    @Type(JsonBinaryType.class)
    @Column(name = "definition_json", columnDefinition = "jsonb")
    private String definitionJson;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "created_by")
    private UUID createdBy;

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
