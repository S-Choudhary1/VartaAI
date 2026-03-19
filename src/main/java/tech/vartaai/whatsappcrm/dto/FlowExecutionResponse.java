package tech.vartaai.whatsappcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FlowExecutionResponse {

    private UUID id;
    private UUID flowId;
    private UUID contactId;
    private UUID campaignId;
    private String currentNodeId;
    private String status;
    private OffsetDateTime startedAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime completedAt;

    private List<StepHistoryItem> steps;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class StepHistoryItem {
        private UUID id;
        private String nodeId;
        private String nodeType;
        private String action;
        private UUID messageId;
        private String responseData;
        private String matchedCondition;
        private OffsetDateTime createdAt;
    }
}
