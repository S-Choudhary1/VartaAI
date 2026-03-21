package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tech.vartaai.whatsappcrm.config.WhatsAppProperties;
import tech.vartaai.whatsappcrm.dto.WhatsAppOnboardRequest;
import tech.vartaai.whatsappcrm.dto.WhatsAppOnboardResponse;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.repository.ClientRepository;

import java.util.UUID;

@Service
@Slf4j
public class WhatsAppOnboardService {

    private final ClientRepository clientRepository;
    private final WhatsAppProperties whatsAppProperties;
    private final WebClient webClient;

    public WhatsAppOnboardService(ClientRepository clientRepository,
                                  WhatsAppProperties whatsAppProperties) {
        this.clientRepository = clientRepository;
        this.whatsAppProperties = whatsAppProperties;
        this.webClient = WebClient.builder()
                .baseUrl("https://graph.facebook.com/v22.0")
                .build();
    }

    public WhatsAppOnboardResponse onboard(UUID clientId, WhatsAppOnboardRequest request) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found: " + clientId));

        log.info("Starting WhatsApp onboarding for client: {}", clientId);

        // Exchange authorization code for access token via Meta OAuth
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
        log.info("tokenResponse {}", tokenResponse);
        String accessToken = tokenResponse.get("access_token").asText();
        log.info("Successfully obtained access token for client: {}", clientId);

        // Update client with WhatsApp credentials
        client.setWabaId(request.getWabaId());
        client.setPhoneNumberId(request.getPhoneNumberId());
        client.setAccessToken(accessToken);
        clientRepository.save(client);

        log.info("WhatsApp onboarding completed for client: {}", clientId);

        return WhatsAppOnboardResponse.builder()
                .connected(true)
                .wabaId(request.getWabaId())
                .phoneNumberId(request.getPhoneNumberId())
                .build();
    }

    public WhatsAppOnboardResponse getStatus(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found: " + clientId));

        boolean connected = client.getAccessToken() != null && !client.getAccessToken().isBlank()
                && client.getWabaId() != null && !client.getWabaId().isBlank()
                && client.getPhoneNumberId() != null && !client.getPhoneNumberId().isBlank();

        return WhatsAppOnboardResponse.builder()
                .connected(connected)
                .wabaId(client.getWabaId())
                .phoneNumberId(client.getPhoneNumberId())
                .build();
    }
}
