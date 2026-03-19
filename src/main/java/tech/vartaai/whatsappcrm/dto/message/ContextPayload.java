package tech.vartaai.whatsappcrm.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reply-to context. When set, the message is sent as a reply to the referenced message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContextPayload {
    private String messageId;
}
