package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.vartaai.whatsappcrm.dto.message.ContactCardPayload;
import tech.vartaai.whatsappcrm.dto.message.ContextPayload;
import tech.vartaai.whatsappcrm.dto.message.InteractivePayload;
import tech.vartaai.whatsappcrm.dto.message.LocationPayload;
import tech.vartaai.whatsappcrm.dto.message.MediaPayload;
import tech.vartaai.whatsappcrm.dto.message.ReactionPayload;
import tech.vartaai.whatsappcrm.dto.message.TemplatePayload;
import tech.vartaai.whatsappcrm.dto.message.TextPayload;
import tech.vartaai.whatsappcrm.entity.Message;

import java.util.List;
import java.util.UUID;

/**
 * Unified request DTO for sending ANY WhatsApp message type.
 * Populate the field matching the messageType and leave others null.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class SendMessageRequest {

    @NotBlank
    private String to;

    @NotNull
    private Message.MessageType messageType;

    // ─── Type-specific payloads (populate ONE matching messageType) ───

    private TextPayload text;
    private MediaPayload image;
    private MediaPayload video;
    private MediaPayload audio;
    private MediaPayload document;
    private MediaPayload sticker;
    private LocationPayload location;
    private List<ContactCardPayload> contacts;
    private InteractivePayload interactive;
    private ReactionPayload reaction;
    private TemplatePayload template;

    // ─── Optional context ────────────────────────────────────────────

    /** Reply-to context — set to send this message as a reply */
    private ContextPayload context;

    // ─── Metadata ────────────────────────────────────────────────────

    private UUID contactId;
    private UUID campaignId;
}
