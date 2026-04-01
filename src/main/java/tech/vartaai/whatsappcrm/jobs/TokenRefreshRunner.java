package tech.vartaai.whatsappcrm.jobs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Client.OnboardingStatus;
import tech.vartaai.whatsappcrm.repository.ClientRepository;
import tech.vartaai.whatsappcrm.service.ProvisioningService;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Slf4j
public class TokenRefreshRunner {

    private final ClientRepository clientRepository;
    private final ProvisioningService provisioningService;

    public TokenRefreshRunner(ClientRepository clientRepository,
                              ProvisioningService provisioningService) {
        this.clientRepository = clientRepository;
        this.provisioningService = provisioningService;
    }

    /**
     * Runs every 6 hours. Finds clients whose tokens expire within 7 days
     * and exchanges them for new long-lived tokens.
     */
    @Scheduled(fixedDelay = 21600000) // 6 hours
    public void refreshExpiringTokens() {
        OffsetDateTime threshold = OffsetDateTime.now().plusDays(7);

        List<Client> clients = clientRepository.findClientsWithExpiringTokens(threshold);
        if (clients.isEmpty()) {
            return;
        }

        log.info("TOKEN_REFRESH_START count={}", clients.size());

        for (Client client : clients) {
            try {
                provisioningService.exchangeLongLivedToken(client);
                clientRepository.save(client);
                log.info("TOKEN_REFRESH_SUCCESS clientId={} newExpiresAt={}", client.getId(), client.getTokenExpiresAt());
            } catch (Exception e) {
                log.error("TOKEN_REFRESH_FAILED clientId={} err={}", client.getId(), e.getMessage());
            }
        }
    }

    /**
     * Runs every 24 hours. Syncs phone details and business profile
     * for all active (READY) clients.
     */
//    @Scheduled(fixedDelay = 86400000) // 24 hours
    public void periodicDataSync() {
        List<Client> clients = clientRepository.findByOnboardingStatus(OnboardingStatus.READY);
        if (clients.isEmpty()) {
            return;
        }

        log.info("PERIODIC_SYNC_START count={}", clients.size());

        for (Client client : clients) {
            provisioningService.refreshClientData(client);
        }
    }
}
