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
public class ClientDto {
    private String id;
    private String name;
    private String phoneNumberId;
    private String wabaId;
    private String language;
    private String onboardingStatus;
    private String businessName;
    private String verifiedName;
    private String qualityRating;
    private String messagingLimitTier;
    private String phoneStatus;
    private String businessVerificationStatus;
    private String accountReviewStatus;
    private String billingStatus;
    private String provisioningError;
    private OffsetDateTime tokenExpiresAt;
    private OffsetDateTime lastSyncedAt;
    private OffsetDateTime createdAt;
    private long unresolvedAlertCount;
}
