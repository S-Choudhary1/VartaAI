package tech.vartaai.whatsappcrm.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextPayload {
    private String body;
    private Boolean previewUrl;
}
