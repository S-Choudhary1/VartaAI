package tech.vartaai.whatsappcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
public class Campaign {

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

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

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }
    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }
    public String getCsvMetadataJson() { return csvMetadataJson; }
    public void setCsvMetadataJson(String csvMetadataJson) { this.csvMetadataJson = csvMetadataJson; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}


