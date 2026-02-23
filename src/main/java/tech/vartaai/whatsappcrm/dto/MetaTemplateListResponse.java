package tech.vartaai.whatsappcrm.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaTemplateListResponse {
    private List<MetaTemplateResponse> data;
    private JsonNode paging;
}
