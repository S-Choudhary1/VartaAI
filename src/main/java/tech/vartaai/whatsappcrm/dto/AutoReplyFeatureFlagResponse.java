package tech.vartaai.whatsappcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoReplyFeatureFlagResponse {
    private UUID clientId;
    private boolean enabled;
}
