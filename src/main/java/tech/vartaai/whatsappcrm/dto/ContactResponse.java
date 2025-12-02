package tech.vartaai.whatsappcrm.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ContactResponse {
    
    private UUID id;
    private String phone;
    private String name;
    private List<String> tags;
    private Map<String, String> metadata;

    public ContactResponse() {
    }

    public ContactResponse(UUID id, String phone, String name, List<String> tags, Map<String, String> metadata) {
        this.id = id;
        this.phone = phone;
        this.name = name;
        this.tags = tags;
        this.metadata = metadata;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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
