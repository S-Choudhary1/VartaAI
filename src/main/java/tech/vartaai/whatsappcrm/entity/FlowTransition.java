package tech.vartaai.whatsappcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "flow_transitions")
@Data
public class FlowTransition {

    public enum MatchOperator { EQUALS, CONTAINS, DEFAULT }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_version_id", nullable = false)
    @JsonIgnore
    private FlowVersion flowVersion;

    @Column(name = "from_step_key", nullable = false, length = 100)
    private String fromStepKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_operator", nullable = false, length = 20)
    private MatchOperator matchOperator;

    @Column(name = "match_value", length = 255)
    private String matchValue;

    @Column(name = "next_step_key", length = 100)
    private String nextStepKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
