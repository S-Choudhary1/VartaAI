package tech.vartaai.whatsappcrm.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class FlowVersionResponse {
    private UUID id;
    private UUID flowId;
    private Integer versionNumber;
    private String status;
    private String startStepKey;
    private OffsetDateTime createdAt;
    private OffsetDateTime publishedAt;
}
