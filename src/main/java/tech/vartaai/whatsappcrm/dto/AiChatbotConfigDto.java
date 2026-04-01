package tech.vartaai.whatsappcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AiChatbotConfigDto {

    private UUID id;
    private UUID clientId;
    private String name;
    private boolean enabled;
    private String provider;
    private String model;
    private String systemPrompt;
    private int maxTokens;
    private double temperature;
    private String fallbackMessage;
    private boolean humanHandoffEnabled;
    private String humanHandoffKeyword;
    private int maxConversationTurns;
    private int conversationTimeoutHours;
    private int maxMessagesPerDayPerContact;

    /** True when an API key has been configured; the key value is never exposed. */
    private boolean hasApiKey;

    /**
     * Write-only: accepted on create/update requests but never returned in responses.
     * When null on update, the existing key is preserved.
     */
    private String apiKey;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
