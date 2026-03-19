package tech.vartaai.whatsappcrm.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for media messages: IMAGE, VIDEO, AUDIO, DOCUMENT, STICKER.
 * Provide either {@code link} (public URL) or {@code mediaId} (previously uploaded via Media API).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaPayload {
    private String link;
    private String mediaId;
    private String caption;
    private String filename;
}
