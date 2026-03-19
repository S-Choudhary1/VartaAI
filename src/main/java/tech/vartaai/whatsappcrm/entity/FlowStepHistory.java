package tech.vartaai.whatsappcrm.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "flow_step_history")
@Data
public class FlowStepHistory {

    public enum StepAction {
        ENTERED, MESSAGE_SENT, RESPONSE_RECEIVED, CONDITION_EVALUATED, DELAYED, COMPLETED, FAILED
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "node_id", nullable = false, length = 255)
    private String nodeId;

    @Column(name = "node_type", length = 50)
    private String nodeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 50)
    private StepAction action;

    @Column(name = "message_id")
    private UUID messageId;

    @Type(JsonBinaryType.class)
    @Column(name = "response_data", columnDefinition = "jsonb")
    private String responseData;

    @Column(name = "matched_condition", length = 255)
    private String matchedCondition;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
