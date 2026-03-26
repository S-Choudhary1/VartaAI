package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tech.vartaai.whatsappcrm.config.WhatsAppProperties;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Client.OnboardingStatus;
import tech.vartaai.whatsappcrm.repository.ClientRepository;

import java.time.OffsetDateTime;

@Service
@Slf4j
public class ProvisioningService {

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final ClientRepository clientRepository;
    private final WhatsAppProperties whatsAppProperties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ProvisioningService(ClientRepository clientRepository,
                               WhatsAppProperties whatsAppProperties,
                               ObjectMapper objectMapper) {
        this.clientRepository = clientRepository;
        this.whatsAppProperties = whatsAppProperties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(whatsAppProperties.getApiBaseUrl())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  PROVISIONING PIPELINE
    // ═══════════════════════════════════════════════════════════════

    public Client provisionClient(Client client) {
        log.info("PROVISION_START clientId={} currentStatus={}", client.getId(), client.getOnboardingStatus());
        client.setProvisioningError(null);

        // Step 1: Exchange for long-lived token
        if (statusBefore(client.getOnboardingStatus(), OnboardingStatus.TOKEN_EXCHANGED)) {
            try {
                exchangeLongLivedToken(client);
                client.setOnboardingStatus(OnboardingStatus.TOKEN_EXCHANGED);
                clientRepository.save(client);
                log.info("PROVISION_TOKEN_EXCHANGED clientId={}", client.getId());
            } catch (Exception e) {
                log.warn("PROVISION_TOKEN_EXCHANGE_FAILED clientId={} err={}", client.getId(), e.getMessage());
            }
        }

        // Step 2: Subscribe app to WABA webhooks
        if (statusBefore(client.getOnboardingStatus(), OnboardingStatus.WEBHOOK_SUBSCRIBED)) {
            try {
                subscribeWebhook(client);
                client.setOnboardingStatus(OnboardingStatus.WEBHOOK_SUBSCRIBED);
                clientRepository.save(client);
                log.info("PROVISION_WEBHOOK_SUBSCRIBED clientId={}", client.getId());
            } catch (Exception e) {
                return failProvisioning(client, "Webhook subscription failed: " + e.getMessage());
            }
        }

        // Step 3: Fetch WABA details + phone details + business profile
        if (statusBefore(client.getOnboardingStatus(), OnboardingStatus.PROFILE_SYNCED)) {
            try {
                syncWabaDetails(client);
                syncPhoneDetails(client);
                syncBusinessProfile(client);
                client.setOnboardingStatus(OnboardingStatus.PROFILE_SYNCED);
                client.setLastSyncedAt(OffsetDateTime.now());
                clientRepository.save(client);
                log.info("PROVISION_PROFILE_SYNCED clientId={}", client.getId());
            } catch (Exception e) {
                return failProvisioning(client, "Profile sync failed: " + e.getMessage());
            }
        }

        // Step 4: Check business verification + billing/payment status
        try {
            checkBusinessVerification(client);
        } catch (Exception e) {
            log.warn("PROVISION_VERIFICATION_CHECK_SKIPPED clientId={} err={}", client.getId(), e.getMessage());
        }

        try {
            checkBillingStatus(client);
        } catch (Exception e) {
            log.warn("PROVISION_BILLING_CHECK_SKIPPED clientId={} err={}", client.getId(), e.getMessage());
        }

        // All steps complete
        client.setOnboardingStatus(OnboardingStatus.READY);
        client.setProvisioningError(null);
        clientRepository.save(client);
        log.info("PROVISION_COMPLETE clientId={} status=READY", client.getId());
        return client;
    }

    // ═══════════════════════════════════════════════════════════════
    //  TOKEN MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    public void exchangeLongLivedToken(Client client) {
        String shortLivedToken = client.getAccessToken();
        if (shortLivedToken == null || shortLivedToken.isBlank()) {
            throw new RuntimeException("No access token to exchange");
        }

        JsonNode response = callWithRetry(() -> webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth/access_token")
                        .queryParam("grant_type", "fb_exchange_token")
                        .queryParam("client_id", whatsAppProperties.getAppId())
                        .queryParam("client_secret", whatsAppProperties.getAppSecret())
                        .queryParam("fb_exchange_token", shortLivedToken)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());

        if (response == null || !response.has("access_token")) {
            throw new RuntimeException("Long-lived token exchange returned no token");
        }

        client.setAccessToken(response.get("access_token").asText());
        if (response.has("expires_in")) {
            client.setTokenExpiresAt(OffsetDateTime.now().plusSeconds(response.get("expires_in").asLong()));
        }

        log.info("TOKEN_EXCHANGED clientId={} expiresAt={}", client.getId(), client.getTokenExpiresAt());
    }

    // ═══════════════════════════════════════════════════════════════
    //  WEBHOOK SUBSCRIPTION
    // ═══════════════════════════════════════════════════════════════

    public void subscribeWebhook(Client client) {
        String wabaId = client.getWabaId();
        String accessToken = client.getAccessToken();

        if (wabaId == null || wabaId.isBlank()) {
            throw new RuntimeException("WABA ID is required for webhook subscription");
        }

        JsonNode response = callWithRetry(() -> webClient.post()
                .uri("/" + wabaId + "/subscribed_apps")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());

        boolean success = response != null && response.path("success").asBoolean(false);
        if (!success) {
            throw new RuntimeException("Webhook subscription returned success=false");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  DATA SYNC
    // ═══════════════════════════════════════════════════════════════

    public void syncWabaDetails(Client client) {
        String wabaId = client.getWabaId();
        String accessToken = client.getAccessToken();

        JsonNode response = callWithRetry(() -> webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/" + wabaId)
                        .queryParam("fields", "name,currency,timezone_id,account_review_status,business_verification_status,ownership_type,message_template_namespace")
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());

        if (response != null) {
            String wabaName = response.path("name").asText(null);
            if (wabaName != null && !wabaName.isBlank()) {
                client.setBusinessName(wabaName);
            }

            String verificationStatus = response.path("business_verification_status").asText(null);
            if (verificationStatus != null) {
                client.setBusinessVerificationStatus(verificationStatus);
            }

            String reviewStatus = response.path("account_review_status").asText(null);
            if (reviewStatus != null) {
                client.setAccountReviewStatus(reviewStatus);
            }

            log.info("WABA_DETAILS_SYNCED clientId={} name={} verification={} review={}",
                    client.getId(), wabaName, verificationStatus, reviewStatus);
        }
    }

    public void syncPhoneDetails(Client client) {
        String phoneNumberId = client.getPhoneNumberId();
        String accessToken = client.getAccessToken();

        JsonNode response = callWithRetry(() -> webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/" + phoneNumberId)
                        .queryParam("fields", "verified_name,code_verification_status,display_phone_number,quality_rating,platform_type,throughput,messaging_limit_tier,is_official_business_account,account_mode,status,name_status")
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());

        if (response != null) {
            client.setVerifiedName(response.path("verified_name").asText(null));
            client.setQualityRating(response.path("quality_rating").asText(null));
            client.setPhoneStatus(response.path("status").asText(null));

            String limitTier = response.path("messaging_limit_tier").asText(null);
            if (limitTier == null || limitTier.isBlank()) {
                limitTier = response.path("throughput").path("level").asText(null);
            }
            client.setMessagingLimitTier(limitTier);

            // account_mode: SANDBOX means no payment method, LIVE means payment is set up
            String accountMode = response.path("account_mode").asText(null);
            if (accountMode != null) {
                if ("LIVE".equalsIgnoreCase(accountMode)) {
                    client.setBillingStatus("ACTIVE");
                } else if ("SANDBOX".equalsIgnoreCase(accountMode)) {
                    client.setBillingStatus("NO_PAYMENT_METHOD");
                }
            }

            log.info("PHONE_DETAILS_SYNCED clientId={} verifiedName={} quality={} limit={} status={} accountMode={}",
                    client.getId(), client.getVerifiedName(), client.getQualityRating(),
                    client.getMessagingLimitTier(), client.getPhoneStatus(), accountMode);
        }
    }

    public void syncBusinessProfile(Client client) {
        String phoneNumberId = client.getPhoneNumberId();
        String accessToken = client.getAccessToken();

        try {
            JsonNode response = callWithRetry(() -> webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + phoneNumberId + "/whatsapp_business_profile")
                            .queryParam("fields", "about,address,description,email,profile_picture_url,websites,vertical")
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block());

            if (response != null && response.has("data") && response.get("data").isArray()
                    && !response.get("data").isEmpty()) {
                client.setBusinessProfileJson(response.get("data").get(0).toString());
                log.info("BUSINESS_PROFILE_SYNCED clientId={}", client.getId());
            }
        } catch (Exception e) {
            log.warn("BUSINESS_PROFILE_FETCH_FAILED clientId={} err={}", client.getId(), e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  BILLING / PAYMENT STATUS CHECK
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if the WABA has a payment method configured.
     * Meta doesn't expose a direct "has payment method" API.
     * We infer from account_mode on the phone number:
     *   - LIVE = payment method is set, conversations are billable
     *   - SANDBOX = no payment method, limited to test conversations
     *
     * This is already done in syncPhoneDetails via account_mode field.
     * This method provides a standalone check by fetching the WABA's
     * account_mode if it wasn't populated during phone sync.
     */
    public void checkBillingStatus(Client client) {
        // If already determined from phone sync, skip
        if (client.getBillingStatus() != null
                && ("ACTIVE".equals(client.getBillingStatus()) || "NO_PAYMENT_METHOD".equals(client.getBillingStatus()))) {
            return;
        }

        String phoneNumberId = client.getPhoneNumberId();
        String accessToken = client.getAccessToken();
        if (phoneNumberId == null || accessToken == null) return;

        try {
            JsonNode response = callWithRetry(() -> webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + phoneNumberId)
                            .queryParam("fields", "account_mode")
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block());

            if (response != null) {
                String accountMode = response.path("account_mode").asText(null);
                if ("LIVE".equalsIgnoreCase(accountMode)) {
                    client.setBillingStatus("ACTIVE");
                } else if ("SANDBOX".equalsIgnoreCase(accountMode)) {
                    client.setBillingStatus("NO_PAYMENT_METHOD");
                } else {
                    client.setBillingStatus("UNKNOWN");
                }
                clientRepository.save(client);
                log.info("BILLING_STATUS_CHECK clientId={} accountMode={} billingStatus={}",
                        client.getId(), accountMode, client.getBillingStatus());
            }
        } catch (Exception e) {
            log.warn("BILLING_STATUS_CHECK_FAILED clientId={} err={}", client.getId(), e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  BUSINESS VERIFICATION
    // ═══════════════════════════════════════════════════════════════

    public void checkBusinessVerification(Client client) {
        String wabaId = client.getWabaId();
        String accessToken = client.getAccessToken();
        if (wabaId == null || accessToken == null) return;

        try {
            JsonNode response = callWithRetry(() -> webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + wabaId)
                            .queryParam("fields", "business_verification_status,account_review_status")
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block());

            if (response != null) {
                client.setBusinessVerificationStatus(response.path("business_verification_status").asText(null));
                client.setAccountReviewStatus(response.path("account_review_status").asText(null));
                clientRepository.save(client);
                log.info("VERIFICATION_CHECK clientId={} verification={} review={}",
                        client.getId(), client.getBusinessVerificationStatus(), client.getAccountReviewStatus());
            }
        } catch (Exception e) {
            log.warn("VERIFICATION_CHECK_FAILED clientId={} err={}", client.getId(), e.getMessage());
        }
    }

    /**
     * Re-sync mutable data without full provisioning. Used by periodic sync jobs.
     */
    public void refreshClientData(Client client) {
        try {
            syncPhoneDetails(client);
            syncBusinessProfile(client);
            checkBusinessVerification(client);
            checkBillingStatus(client);
            client.setLastSyncedAt(OffsetDateTime.now());
            clientRepository.save(client);
            log.info("CLIENT_DATA_REFRESHED clientId={}", client.getId());
        } catch (Exception e) {
            log.error("CLIENT_DATA_REFRESH_FAILED clientId={} err={}", client.getId(), e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  RETRY WITH EXPONENTIAL BACKOFF
    // ═══════════════════════════════════════════════════════════════

    private <T> T callWithRetry(java.util.function.Supplier<T> apiCall) {
        int attempt = 0;
        long backoffMs = INITIAL_BACKOFF_MS;

        while (true) {
            try {
                return apiCall.get();
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean retryable = (status == 429 || status >= 500);
                if (!retryable || attempt >= MAX_RETRIES) throw e;
                attempt++;
                log.warn("META_API_RETRY attempt={}/{} status={} backoff={}ms", attempt, MAX_RETRIES, status, backoffMs);
                try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw e; }
                backoffMs *= 2;
            }
        }
    }

    private Client failProvisioning(Client client, String error) {
        log.error("PROVISION_FAILED clientId={} err={}", client.getId(), error);
        client.setOnboardingStatus(OnboardingStatus.FAILED);
        client.setProvisioningError(error);
        clientRepository.save(client);
        return client;
    }

    private boolean statusBefore(OnboardingStatus current, OnboardingStatus target) {
        if (current == null || current == OnboardingStatus.NOT_STARTED || current == OnboardingStatus.FAILED) return true;
        return current.ordinal() < target.ordinal();
    }
}
