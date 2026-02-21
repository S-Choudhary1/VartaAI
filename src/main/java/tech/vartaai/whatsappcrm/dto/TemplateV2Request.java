package tech.vartaai.whatsappcrm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateV2Request {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "Language code is required")
    @JsonProperty("language_code")
    private String languageCode;

    @NotNull(message = "Components are required")
    @NotEmpty(message = "Components must not be empty")
    @Valid
    private List<TemplateComponentRequest> components;
}
