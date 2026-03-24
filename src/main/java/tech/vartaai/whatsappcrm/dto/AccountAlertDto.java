package tech.vartaai.whatsappcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AccountAlertDto {
    private String id;
    private String category;
    private String severity;
    private String title;
    private String message;
    private String metaEventField;
    private boolean resolved;
    private OffsetDateTime createdAt;
}
