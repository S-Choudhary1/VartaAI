package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AutoReplyRuleStatusRequest {
    @NotNull(message = "Active is required")
    private Boolean active;
}
