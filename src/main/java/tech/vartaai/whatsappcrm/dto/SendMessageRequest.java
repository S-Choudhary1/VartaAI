package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
@Data
public class SendMessageRequest {
    @NotBlank
    private String to;
    private String text; // Optional custom text
    private UUID contactId;
    private UUID templateId;
    private Map<String, String> variables;
    private String provider; // META
    private String campaignId;
}


