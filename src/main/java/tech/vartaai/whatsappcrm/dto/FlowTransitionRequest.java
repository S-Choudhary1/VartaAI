package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FlowTransitionRequest {
    @NotBlank
    private String fromStepKey;
    @NotBlank
    private String operator; // EQUALS|CONTAINS|DEFAULT
    private String matchValue;
    private String nextStepKey; // null => end flow
}
