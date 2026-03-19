package tech.vartaai.whatsappcrm.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationPayload {
    private Double latitude;
    private Double longitude;
    private String name;
    private String address;
}
