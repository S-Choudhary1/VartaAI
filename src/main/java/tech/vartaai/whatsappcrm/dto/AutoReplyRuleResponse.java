package tech.vartaai.whatsappcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.vartaai.whatsappcrm.entity.AutoReplyRule;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoReplyRuleResponse {
    private UUID id;
    private String name;
    private AutoReplyRule.RuleType ruleType;
    private String matchValue;
    private AutoReplyRule.ResponseType responseType;
    private String responseText;
    private UUID templateId;
    private boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
