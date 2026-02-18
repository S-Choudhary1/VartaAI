package tech.vartaai.whatsappcrm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TemplateRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @JsonProperty("provider_template_id")
    private String providerTemplateId;

    @NotBlank(message = "Type is required")
    private String type; // TEXT, MEDIA, INTERACTIVE

    @NotNull(message = "Content is required")
    private String content;

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
}
