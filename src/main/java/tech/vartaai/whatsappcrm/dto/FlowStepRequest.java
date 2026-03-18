package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class FlowStepRequest {
    @NotBlank
    private String stepKey;
    @NotBlank
    private String messageType; // TEMPLATE|TEXT (v1), extensible for future types
    private UUID templateId;
    private String textBody;
    private String payloadJson;
}
