package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlowRequest {

    @NotBlank
    private String name;

    private String description;

    private String triggerType;

    private String triggerKeywords;

    private String definitionJson;
}
