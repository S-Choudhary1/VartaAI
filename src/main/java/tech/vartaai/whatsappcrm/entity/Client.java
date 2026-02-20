package tech.vartaai.whatsappcrm.entity;

import jakarta.persistence.*;
import lombok.Data;
import tech.vartaai.whatsappcrm.dto.ClientDto;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "clients")
@Data
public class Client {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "api_key", length = 255)
    private String apiKey;

    @Column(name = "phone_number_id", length = 50)
    private String phoneNumberId;

    @Column(name = "waba_id", length = 50)
    private String wabaId;

    @Column(name = "access_token", length = 500) // Access tokens can be long
    private String accessToken;

    /**
     * Default language for messages sent on behalf of this client.
     * Example values: en_US, hi_IN, es_ES, etc.
     */
    @Column(name = "language", length = 32)
    private String language;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (language == null || language.isBlank()) {
            // Default client language is English
            language = "en_US";
        }
    }
    public ClientDto toDto() {
        return ClientDto.builder()
                .id(this.id.toString())
                .name(this.name)
                .wabaId(this.wabaId)
                .phoneNumberId(this.phoneNumberId)
                .createdAt(this.createdAt)
                .build();
    }
}

