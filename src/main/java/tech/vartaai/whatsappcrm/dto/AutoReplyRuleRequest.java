package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tech.vartaai.whatsappcrm.entity.AutoReplyRule;

import java.util.UUID;

@Data
public class AutoReplyRuleRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Rule type is required")
    private AutoReplyRule.RuleType ruleType;

    private String matchValue;

    @NotNull(message = "Response type is required")
    private AutoReplyRule.ResponseType responseType;

    private String responseText;

    private UUID templateId;

    private boolean active = true;
}
