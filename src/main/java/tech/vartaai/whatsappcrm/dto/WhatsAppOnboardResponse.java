package tech.vartaai.whatsappcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WhatsAppOnboardResponse {
    private boolean connected;
    private String wabaId;
    private String phoneNumberId;
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
    private long unresolvedAlertCount;
}
