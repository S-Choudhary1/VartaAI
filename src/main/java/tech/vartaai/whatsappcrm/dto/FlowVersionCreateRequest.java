package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class FlowVersionCreateRequest {
    @NotBlank
    private String startStepKey;
    @Valid
    @NotEmpty
    private List<FlowStepRequest> steps;
    @Valid
    @NotEmpty
    private List<FlowTransitionRequest> transitions;
    private boolean publish;
}
