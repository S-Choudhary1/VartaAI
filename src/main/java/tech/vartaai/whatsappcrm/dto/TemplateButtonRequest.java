package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateButtonRequest {

    @NotBlank(message = "Button type is required")
    private String type;

    private String text;
    private String url;
    private String phoneNumber;
}
