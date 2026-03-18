package tech.vartaai.whatsappcrm.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class FlowRunReportItem {
    private UUID runId;
    private String contactId;
    private String phone;
    private String currentStep;
    private String lastResponse;
    private String status;
    private boolean completed;
    private boolean failed;
    private OffsetDateTime startedAt;
    private OffsetDateTime updatedAt;
}
