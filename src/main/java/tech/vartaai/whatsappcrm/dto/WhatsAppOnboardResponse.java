package tech.vartaai.whatsappcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WhatsAppOnboardResponse {
    private boolean connected;
    private String wabaId;
    private String phoneNumberId;
}
