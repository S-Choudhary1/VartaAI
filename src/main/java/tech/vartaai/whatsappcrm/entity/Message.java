package tech.vartaai.whatsappcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Data
public class Message {

    public enum Direction { OUTGOING, INCOMING }
    public enum Status { QUEUED, SENT, DELIVERED, READ, FAILED, UNKNOWN }

    /**
     * WhatsApp message types supported across Cloud API send/receive flows.
     * Unknown values are safely mapped to UNKNOWN to avoid runtime failures
     * when Meta introduces new types.
     */
    public enum MessageType {
        TEXT,
        TEMPLATE,
        INTERACTIVE,
        BUTTON,
        REACTION,
        IMAGE,
        VIDEO,
        AUDIO,
        DOCUMENT,
        STICKER,
        LOCATION,
        CONTACTS,
        ORDER,
        SYSTEM,
        UNKNOWN;

        public static MessageType fromValue(String value) {
            if (value == null || value.isBlank()) {
                return UNKNOWN;
            }

            String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase();
            try {
                return MessageType.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return UNKNOWN;
            }
        }
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    @JsonIgnore
    private Client client;

    @Column(name = "campaign_id")
    private String campaignId;

    @Column(name = "contact_id")
    private String contactId;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 50)
    private MessageType messageType;

    @Column(name = "context_message_id", length = 255)
    private String contextMessageId;

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

}


