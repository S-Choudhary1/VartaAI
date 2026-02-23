package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.vartaai.whatsappcrm.entity.Message;

import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class SendMessageRequest {
    @NotBlank
    private String to;
    private Message.MessageType messageType;
    private String text; // Optional custom text
    private UUID contactId;
    private UUID templateId;
    private Map<String, String> variables;
    private String provider; // META
    private String campaignId;
}


