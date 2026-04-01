package tech.vartaai.whatsappcrm.entity;

import jakarta.persistence.*;
import lombok.Data;
import tech.vartaai.whatsappcrm.dto.ClientDto;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "clients")
@Data
public class Client {

    public enum OnboardingStatus {
        NOT_STARTED,
        TOKEN_OBTAINED,
        TOKEN_EXCHANGED,
        WEBHOOK_SUBSCRIBED,
        PROFILE_SYNCED,
        READY,
        FAILED
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "api_key", length = 255)
    private String apiKey;

    @Column(name = "phone_number_id", length = 50)
    private String phoneNumberId;

    @Column(name = "waba_id", length = 50)
    private String wabaId;

    @Column(name = "access_token", length = 500)
    private String accessToken;

    @Column(name = "token_expires_at")
    private OffsetDateTime tokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", length = 30)
    private OnboardingStatus onboardingStatus;

    @Column(name = "business_name", length = 255)
    private String businessName;

    @Column(name = "verified_name", length = 255)
    private String verifiedName;

    @Column(name = "quality_rating", length = 20)
    private String qualityRating;

    @Column(name = "messaging_limit_tier", length = 50)
    private String messagingLimitTier;

    @Column(name = "phone_status", length = 30)
    private String phoneStatus;

    @Column(name = "business_profile_json", columnDefinition = "TEXT")
    private String businessProfileJson;

    @Column(name = "business_verification_status", length = 50)
    private String businessVerificationStatus;

    @Column(name = "account_review_status", length = 50)
    private String accountReviewStatus;

    @Column(name = "billing_status", length = 30)
    private String billingStatus;

    @Column(name = "provisioning_error", length = 1000)
    private String provisioningError;

    @Column(name = "ai_chatbot_enabled", nullable = false)
    private boolean aiChatbotEnabled;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    /**
     * Default language for messages sent on behalf of this client.
     * Example values: en_US, hi_IN, es_ES, etc.
     */
    @Column(name = "language", length = 32)
    private String language;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (language == null || language.isBlank()) {
            language = "en_US";
        }
        if (onboardingStatus == null) {
            onboardingStatus = OnboardingStatus.NOT_STARTED;
        }
    }

    public ClientDto toDto() {
        return ClientDto.builder()
                .id(this.id.toString())
                .name(this.name)
                .wabaId(this.wabaId)
                .phoneNumberId(this.phoneNumberId)
                .language(this.language)
                .onboardingStatus(this.onboardingStatus != null ? this.onboardingStatus.name() : null)
                .businessName(this.businessName)
                .verifiedName(this.verifiedName)
                .qualityRating(this.qualityRating)
                .messagingLimitTier(this.messagingLimitTier)
                .phoneStatus(this.phoneStatus)
                .businessVerificationStatus(this.businessVerificationStatus)
                .accountReviewStatus(this.accountReviewStatus)
                .billingStatus(this.billingStatus)
                .provisioningError(this.provisioningError)
                .aiChatbotEnabled(this.aiChatbotEnabled)
                .tokenExpiresAt(this.tokenExpiresAt)
                .lastSyncedAt(this.lastSyncedAt)
                .createdAt(this.createdAt)
                .build();
    }
}

