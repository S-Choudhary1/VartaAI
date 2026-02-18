package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AutoReplyFeatureFlagRequest {
    @NotNull(message = "Enabled is required")
    private Boolean enabled;
}
