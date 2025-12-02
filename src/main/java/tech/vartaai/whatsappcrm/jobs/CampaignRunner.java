package tech.vartaai.whatsappcrm.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.vartaai.whatsappcrm.entity.Campaign;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.entity.Template;
import tech.vartaai.whatsappcrm.integration.provider.SendResponse;
import tech.vartaai.whatsappcrm.integration.provider.WhatsAppProvider;
import tech.vartaai.whatsappcrm.repository.CampaignRepository;
import tech.vartaai.whatsappcrm.repository.MessageRepository;
import tech.vartaai.whatsappcrm.repository.TemplateRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class CampaignRunner {

    private final CampaignRepository campaignRepository;
    private final TemplateRepository templateRepository;
    private final MessageRepository messageRepository;
    private final WhatsAppProvider provider;

    public CampaignRunner(CampaignRepository campaignRepository,
                          TemplateRepository templateRepository,
                          MessageRepository messageRepository,
                          WhatsAppProvider provider) {
        this.campaignRepository = campaignRepository;
        this.templateRepository = templateRepository;
        this.messageRepository = messageRepository;
        this.provider = provider;
    }

    // Simple runner placeholder: picks PENDING campaigns scheduled in past and marks RUNNING
    @Scheduled(fixedDelay = 5000)
    public void runCampaigns() throws InterruptedException {
        // In a full impl, query by status/schedule and recipients list; here we just noop
        // This is a placeholder to show scheduling wiring per PRD
    }
}


