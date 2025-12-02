package tech.vartaai.whatsappcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class Message {

    public enum Direction { OUTGOING, INCOMING }
    public enum Status { SENT, DELIVERED, FAILED, READ }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    @JsonIgnore
    private Client client;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "provider", length = 50)
    private String provider; // META, etc

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private Status status;

    @Type(JsonBinaryType.class)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payloadJson;

    @Type(JsonBinaryType.class)
    @Column(name = "response", columnDefinition = "jsonb")
    private String responseJson;

    @Column(name = "error")
    private String error;

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
    public UUID getCampaignId() { return campaignId; }
    public void setCampaignId(UUID campaignId) { this.campaignId = campaignId; }
    public UUID getContactId() { return contactId; }
    public void setContactId(UUID contactId) { this.contactId = contactId; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}


