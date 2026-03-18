package tech.vartaai.whatsappcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.vartaai.whatsappcrm.entity.Client;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CampaignDto {

    private UUID id;

    private Client client;
    private String name;

    private UUID templateId;
    private UUID flowVersionId;
    private UUID uploadedBy;
    private String csvMetadataJson;
    @Builder.Default
    private Status status = Status.PENDING;
    private OffsetDateTime scheduledAt;
    private OffsetDateTime createdAt;
    private Integer totalContacts;
    private Integer processedContacts;
    }