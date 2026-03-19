package tech.vartaai.whatsappcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;
import tech.vartaai.whatsappcrm.dto.CampaignDto;
import tech.vartaai.whatsappcrm.dto.Status;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
@Data
public class Campaign {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    @JsonIgnore
    private Client client;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Type(JsonBinaryType.class)
    @Column(name = "csv_metadata", columnDefinition = "jsonb")
    private String csvMetadataJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private Status status = Status.PENDING;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_contacts")
    private Integer processedContacts;

    @Column(name = "total_contacts")
    private Integer totalContacts;

    @Column(name = "flow_id")
    private UUID flowId;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public CampaignDto toDto() {
        return CampaignDto.builder()
                .id(this.id)
//                .client(this.client)
                .totalContacts(this.totalContacts)
                .processedContacts(this.processedContacts)
                .name(this.name)
                .templateId(this.templateId)
                .status(this.status)
                .uploadedBy(this.uploadedBy)
                .createdAt(this.createdAt)
                .scheduledAt(this.scheduledAt)
                .csvMetadataJson(this.csvMetadataJson)
                .flowId(this.flowId)
                .build();
    }
}


