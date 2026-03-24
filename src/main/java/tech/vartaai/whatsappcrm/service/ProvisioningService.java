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
import java.util.Map;

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

    /**
     * Runs the full post-onboard provisioning pipeline.
     * Each step is wrapped in try-catch so partial progress is saved.
     */
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
                // Non-fatal: short-lived token still works, continue provisioning
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

        // Step 4: Attach billing credit line (if configured)
        try {
            attachBillingCreditLine(client);
        } catch (Exception e) {
            log.warn("PROVISION_BILLING_SKIPPED clientId={} err={}", client.getId(), e.getMessage());
            // Non-fatal: billing can be attached later
        }

        // Step 5: Check business verification status
        try {
            checkBusinessVerification(client);
        } catch (Exception e) {
            log.warn("PROVISION_VERIFICATION_CHECK_SKIPPED clientId={} err={}", client.getId(), e.getMessage());
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

    /**
     * Exchange short-lived user token for long-lived token (60 days).
     * Meta endpoint: GET /oauth/access_token?grant_type=fb_exchange_token&...
     */
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

        String longLivedToken = response.get("access_token").asText();
        client.setAccessToken(longLivedToken);

        if (response.has("expires_in")) {
            long expiresInSeconds = response.get("expires_in").asLong();
            client.setTokenExpiresAt(OffsetDateTime.now().plusSeconds(expiresInSeconds));
        }

        log.info("TOKEN_EXCHANGED clientId={} expiresAt={}", client.getId(), client.getTokenExpiresAt());
    }

    // ═══════════════════════════════════════════════════════════════
    //  WEBHOOK SUBSCRIPTION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Subscribe this app to receive webhooks for the WABA.
     * Meta endpoint: POST /{WABA-ID}/subscribed_apps
     */
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
            log.warn("WEBHOOK_SUBSCRIBE_RESPONSE clientId={} response={}", client.getId(), response);
            throw new RuntimeException("Webhook subscription returned success=false");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  DATA SYNC (Phase 2)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fetch WABA details and store business name + verification status.
     */
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

    /**
     * Fetch phone number details: verified name, quality rating, messaging limits, status.
     */
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

            log.info("PHONE_DETAILS_SYNCED clientId={} verifiedName={} quality={} limit={} status={}",
                    client.getId(), client.getVerifiedName(), client.getQualityRating(),
                    client.getMessagingLimitTier(), client.getPhoneStatus());
        }
    }

    /**
     * Fetch WhatsApp business profile (about, address, etc.).
     */
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
            log.warn("BUSINESS_PROFILE_FETCH_FAILED clientId={} status={}", client.getId(), e.getMessage());
            // Non-fatal
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  BILLING (Phase 3)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Attach credit line to WABA for billing.
     * Meta endpoint: POST /{CREDIT-LINE-ID}/whatsapp_credit_sharing_and_attach
     * Only runs if credit line ID is configured.
     */
    public void attachBillingCreditLine(Client client) {
        String creditLineId = whatsAppProperties.getCreditLineId();
        if (creditLineId == null || creditLineId.isBlank()) {
            log.debug("BILLING_SKIP no credit_line_id configured");
            return;
        }

        String wabaId = client.getWabaId();
        String accessToken = client.getAccessToken();

        if (wabaId == null || wabaId.isBlank()) {
            log.warn("BILLING_SKIP no WABA ID for clientId={}", client.getId());
            return;
        }

        try {
            JsonNode response = callWithRetry(() -> webClient.post()
                    .uri("/" + creditLineId + "/whatsapp_credit_sharing_and_attach")
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(Map.of("waba_id", wabaId, "waba_currency", "USD"))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block());

            String allocationId = response != null ? response.path("allocation_config_id").asText(null) : null;
            if (allocationId != null) {
                client.setBillingStatus("ATTACHED");
                log.info("BILLING_ATTACHED clientId={} allocationId={}", client.getId(), allocationId);
            } else {
                client.setBillingStatus("PENDING");
                log.info("BILLING_RESPONSE clientId={} response={}", client.getId(), response);
            }
        } catch (WebClientResponseException e) {
            // 400 with "already shared" is fine
            if (e.getResponseBodyAsString().contains("already")) {
                client.setBillingStatus("ATTACHED");
                log.info("BILLING_ALREADY_ATTACHED clientId={}", client.getId());
            } else {
                client.setBillingStatus("FAILED");
                log.warn("BILLING_ATTACH_FAILED clientId={} status={} body={}",
                        client.getId(), e.getStatusCode(), e.getResponseBodyAsString());
                throw new RuntimeException("Billing attachment failed", e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  BUSINESS VERIFICATION (Phase 3)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check and store business verification status.
     * Uses data already fetched in syncWabaDetails; this method can also be called standalone.
     */
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
                String verificationStatus = response.path("business_verification_status").asText(null);
                String reviewStatus = response.path("account_review_status").asText(null);

                client.setBusinessVerificationStatus(verificationStatus);
                client.setAccountReviewStatus(reviewStatus);
                clientRepository.save(client);

                log.info("VERIFICATION_CHECK clientId={} verification={} review={}",
                        client.getId(), verificationStatus, reviewStatus);
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
            client.setLastSyncedAt(OffsetDateTime.now());
            clientRepository.save(client);
            log.info("CLIENT_DATA_REFRESHED clientId={}", client.getId());
        } catch (Exception e) {
            log.error("CLIENT_DATA_REFRESH_FAILED clientId={} err={}", client.getId(), e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  RETRY WITH EXPONENTIAL BACKOFF (Phase 3)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Execute a Meta API call with exponential backoff retry.
     * Retries on 429 (rate limit), 500, 502, 503, 504 errors.
     */
    private <T> T callWithRetry(java.util.function.Supplier<T> apiCall) {
        int attempt = 0;
        long backoffMs = INITIAL_BACKOFF_MS;

        while (true) {
            try {
                return apiCall.get();
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean retryable = (status == 429 || status >= 500);

                if (!retryable || attempt >= MAX_RETRIES) {
                    throw e;
                }

                attempt++;
                log.warn("META_API_RETRY attempt={}/{} status={} backoff={}ms", attempt, MAX_RETRIES, status, backoffMs);

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }

                backoffMs *= 2; // exponential backoff
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    private Client failProvisioning(Client client, String error) {
        log.error("PROVISION_FAILED clientId={} err={}", client.getId(), error);
        client.setOnboardingStatus(OnboardingStatus.FAILED);
        client.setProvisioningError(error);
        clientRepository.save(client);
        return client;
    }

    private boolean statusBefore(OnboardingStatus current, OnboardingStatus target) {
        if (current == null || current == OnboardingStatus.NOT_STARTED || current == OnboardingStatus.FAILED) {
            return true;
        }
        return current.ordinal() < target.ordinal();
    }
}
