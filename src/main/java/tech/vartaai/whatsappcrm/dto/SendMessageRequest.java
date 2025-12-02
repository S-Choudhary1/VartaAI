package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;

public class SendMessageRequest {
    @NotBlank
    private String to;
    private String text; // Optional custom text
    private UUID contactId;
    private UUID templateId;
    private Map<String, String> variables;
    private String provider; // META

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public UUID getContactId() { return contactId; }
    public void setContactId(UUID contactId) { this.contactId = contactId; }
    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }
    public Map<String, String> getVariables() { return variables; }
    public void setVariables(Map<String, String> variables) { this.variables = variables; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
}


