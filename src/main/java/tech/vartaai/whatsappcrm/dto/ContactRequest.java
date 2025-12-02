package tech.vartaai.whatsappcrm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public class ContactRequest {
    
    @NotBlank(message = "Phone is required")
    private String phone;
    
    private String name;
    
    @JsonProperty("tags")
    private List<String> tags;
    
    @JsonProperty("metadata")
    private Map<String, String> metadata;

    public ContactRequest() {
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
