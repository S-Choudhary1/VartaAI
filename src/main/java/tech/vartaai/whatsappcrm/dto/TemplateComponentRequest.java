package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateComponentRequest {

    @NotBlank(message = "Component type is required")
    private String type;

    private String format;
    private String text;

    @Valid
    private List<TemplateButtonRequest> buttons;

    private List<String> sampleValues;
}
