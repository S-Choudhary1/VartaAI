package tech.vartaai.whatsappcrm.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class FlowResponse {
    private UUID id;
    private String name;
    private String description;
    private boolean active;
    private OffsetDateTime createdAt;
}
