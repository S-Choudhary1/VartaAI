package tech.vartaai.whatsappcrm.dto;

import com.fasterxml.jackson.databind.JsonNode;
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
    private String rejectionReason;
    private String specificRejectionReason;
    private JsonNode components;
    private JsonNode raw;
}
