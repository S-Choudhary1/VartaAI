package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FlowCreateWithVersionRequest {
    @NotBlank
    private String name;

    private String description;

    @Valid
    @NotNull
    private FlowVersionCreateRequest version;
}
