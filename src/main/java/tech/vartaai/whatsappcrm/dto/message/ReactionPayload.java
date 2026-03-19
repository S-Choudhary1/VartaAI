package tech.vartaai.whatsappcrm.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for reaction messages.
 * Set emoji to "" (empty string) to remove a reaction.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReactionPayload {
    private String messageId;
    private String emoji;
}
