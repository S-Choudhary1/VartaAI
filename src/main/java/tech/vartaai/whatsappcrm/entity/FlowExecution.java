package tech.vartaai.whatsappcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "flow_executions")
@Data
public class FlowExecution {

    public enum ExecutionStatus { ACTIVE, WAITING, COMPLETED, FAILED, TIMED_OUT }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_id", nullable = false)
    @JsonIgnore
    private Flow flow;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "current_node_id", length = 255)
    private String currentNodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private ExecutionStatus status = ExecutionStatus.ACTIVE;

    @Column(name = "resume_after")
    private OffsetDateTime resumeAfter;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (startedAt == null) startedAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
