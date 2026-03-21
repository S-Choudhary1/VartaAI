package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WhatsAppOnboardRequest {

    @NotBlank(message = "Authorization code is required")
    private String code;

    @NotBlank(message = "WABA ID is required")
    private String wabaId;

    @NotBlank(message = "Phone Number ID is required")
    private String phoneNumberId;
}
