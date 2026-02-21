package tech.vartaai.whatsappcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaTemplateResponse {
    private String id;
    private String name;
    private String status;
    private String category;
    private String language;
    private String qualityScore;
}
