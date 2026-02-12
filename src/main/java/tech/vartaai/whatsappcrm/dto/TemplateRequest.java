package tech.vartaai.whatsappcrm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class TemplateRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @JsonProperty("provider_template_id")
    private String providerTemplateId;

    @NotBlank(message = "Type is required")
    private String type; // TEXT, MEDIA, INTERACTIVE

    @NotNull(message = "Content is required")
    @JsonProperty("content")
    private Map<String, Object> content;

    /**
     * Optional language code for this template (e.g. en_US, hi_IN).
     * If not provided, the client's default language (if set) will be used.
     */
    @JsonProperty("language_code")
    private String languageCode;

    /**
     * Optional high-level interaction type for this template.
     * Example values: CHOICE, EXTERNAL_LINK.
     * Used by the app/UI to handle the flow after the user taps.
     */
    @JsonProperty("interaction_type")
    private String interactionType;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProviderTemplateId() { return providerTemplateId; }
    public void setProviderTemplateId(String providerTemplateId) { this.providerTemplateId = providerTemplateId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, Object> getContent() { return content; }
    public void setContent(Map<String, Object> content) { this.content = content; }
    public String getLanguageCode() { return languageCode; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }
    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String interactionType) { this.interactionType = interactionType; }
}
