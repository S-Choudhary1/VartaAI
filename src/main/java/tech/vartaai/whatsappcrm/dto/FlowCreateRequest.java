package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FlowCreateRequest {
    @NotBlank
    private String name;
    private String description;
}
