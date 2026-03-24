package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tech.vartaai.whatsappcrm.config.WhatsAppProperties;
import tech.vartaai.whatsappcrm.dto.WhatsAppOnboardRequest;
import tech.vartaai.whatsappcrm.dto.WhatsAppOnboardResponse;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Client.OnboardingStatus;
import tech.vartaai.whatsappcrm.repository.ClientRepository;

import java.util.UUID;

@Service
@Slf4j
public class WhatsAppOnboardService {

    private final ClientRepository clientRepository;
    private final WhatsAppProperties whatsAppProperties;
    private final ProvisioningService provisioningService;
    private final AccountAlertService alertService;
    private final WebClient webClient;

    public WhatsAppOnboardService(ClientRepository clientRepository,
                                  WhatsAppProperties whatsAppProperties,
                                  ProvisioningService provisioningService,
                                  AccountAlertService alertService) {
        this.clientRepository = clientRepository;
        this.whatsAppProperties = whatsAppProperties;
        this.provisioningService = provisioningService;
        this.alertService = alertService;
        this.webClient = WebClient.builder()
                .baseUrl(whatsAppProperties.getApiBaseUrl())
                .build();
    }

    public WhatsAppOnboardResponse onboard(UUID clientId, WhatsAppOnboardRequest request) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found: " + clientId));

        log.info("Starting WhatsApp onboarding for client: {}", clientId);

        // Step 1: Exchange authorization code for access token via Meta OAuth
        JsonNode tokenResponse = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth/access_token")
                        .queryParam("client_id", whatsAppProperties.getAppId())
                        .queryParam("client_secret", whatsAppProperties.getAppSecret())
                        .queryParam("code", request.getCode())
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (tokenResponse == null || !tokenResponse.has("access_token")) {
            log.error("Failed to exchange code for access token. Response: {}", tokenResponse);
            throw new RuntimeException("Failed to exchange authorization code for access token");
        }

        String accessToken = tokenResponse.get("access_token").asText();
        log.info("Successfully obtained access token for client: {}", clientId);

        // Step 2: Store credentials and mark initial status
        client.setWabaId(request.getWabaId());
        client.setPhoneNumberId(request.getPhoneNumberId());
        client.setAccessToken(accessToken);
        client.setOnboardingStatus(OnboardingStatus.TOKEN_OBTAINED);
        client.setProvisioningError(null);
        clientRepository.save(client);

        // Step 3: Run provisioning pipeline (token exchange, webhook subscribe, profile sync)
        client = provisioningService.provisionClient(client);

        log.info("WhatsApp onboarding completed for client: {} status: {}", clientId, client.getOnboardingStatus());

        return buildResponse(client);
    }

    /**
     * Retry provisioning for a client that previously failed or is partially provisioned.
     */
    public WhatsAppOnboardResponse retryProvisioning(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found: " + clientId));

        if (client.getAccessToken() == null || client.getAccessToken().isBlank()) {
            throw new RuntimeException("Client has no access token. Please re-onboard via Embedded Signup.");
        }

        client = provisioningService.provisionClient(client);
        return buildResponse(client);
    }

    public WhatsAppOnboardResponse getStatus(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found: " + clientId));

        return buildResponse(client);
    }

    private WhatsAppOnboardResponse buildResponse(Client client) {
        boolean connected = client.getOnboardingStatus() == OnboardingStatus.READY
                || (client.getAccessToken() != null && !client.getAccessToken().isBlank()
                && client.getWabaId() != null && !client.getWabaId().isBlank()
                && client.getPhoneNumberId() != null && !client.getPhoneNumberId().isBlank());

        return WhatsAppOnboardResponse.builder()
                .connected(connected)
                .wabaId(client.getWabaId())
                .phoneNumberId(client.getPhoneNumberId())
                .onboardingStatus(client.getOnboardingStatus() != null
                        ? client.getOnboardingStatus().name() : null)
                .businessName(client.getBusinessName())
                .verifiedName(client.getVerifiedName())
                .qualityRating(client.getQualityRating())
                .messagingLimitTier(client.getMessagingLimitTier())
                .phoneStatus(client.getPhoneStatus())
                .businessVerificationStatus(client.getBusinessVerificationStatus())
                .accountReviewStatus(client.getAccountReviewStatus())
                .billingStatus(client.getBillingStatus())
                .provisioningError(client.getProvisioningError())
                .unresolvedAlertCount(alertService.getUnresolvedCount(client.getId()))
                .build();
    }
}
