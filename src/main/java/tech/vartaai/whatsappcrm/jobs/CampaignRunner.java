package tech.vartaai.whatsappcrm.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.vartaai.whatsappcrm.dto.SendMessageRequest;
import tech.vartaai.whatsappcrm.dto.Status;
import tech.vartaai.whatsappcrm.dto.message.TemplatePayload;
import tech.vartaai.whatsappcrm.entity.Campaign;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.repository.CampaignRepository;
import tech.vartaai.whatsappcrm.service.MessageService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class CampaignRunner {

    private final CampaignRepository campaignRepository;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    public CampaignRunner(CampaignRepository campaignRepository,
                          MessageService messageService,
                          ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.messageService = messageService;
        this.objectMapper = objectMapper;
    }

//    @Scheduled(fixedDelay = 10000)
    public void runPendingCampaigns() {
        List<Campaign> campaigns = campaignRepository.findReadyToRun(Status.PENDING, OffsetDateTime.now());

        for (Campaign campaign : campaigns) {
            try {
                processCampaign(campaign);
            } catch (Exception e) {
                log.error("CAMPAIGN_RUNNER_ERROR campaignId={} err={}", campaign.getId(), e.getMessage());
                campaign.setStatus(Status.FAILED);
                campaignRepository.save(campaign);
            }
        }
    }

    private void processCampaign(Campaign campaign) {
        log.info("CAMPAIGN_START campaignId={} name={}", campaign.getId(), campaign.getName());

        campaign.setStatus(Status.RUNNING);
        campaign.setProcessedContacts(0);
        campaignRepository.save(campaign);

        UUID clientId = campaign.getClient().getId();
        UUID templateId = campaign.getTemplateId();

        // Parse targets from CSV metadata
        List<Map<String, Object>> targets;
        try {
            Map<String, Object> meta = objectMapper.readValue(campaign.getCsvMetadataJson(),
                    new TypeReference<Map<String, Object>>() {});
            targets = (List<Map<String, Object>>) meta.get("targets");
        } catch (Exception e) {
            log.error("CAMPAIGN_CSV_PARSE_FAILED campaignId={}", campaign.getId(), e);
            campaign.setStatus(Status.FAILED);
            campaignRepository.save(campaign);
            return;
        }

        if (targets == null || targets.isEmpty()) {
            log.warn("CAMPAIGN_NO_TARGETS campaignId={}", campaign.getId());
            campaign.setStatus(Status.COMPLETED);
            campaign.setProcessedContacts(0);
            campaignRepository.save(campaign);
            return;
        }

        int successCount = 0;
        int totalTargets = targets.size();

        for (Map<String, Object> target : targets) {
            String phone = target.get("phone") != null ? target.get("phone").toString() : null;
            if (phone == null || phone.isBlank()) continue;

            try {
                // Build variables map from target
                Map<String, String> variables = new java.util.HashMap<>();
                Object varsObj = target.get("variables");
                if (varsObj instanceof Map) {
                    ((Map<?, ?>) varsObj).forEach((k, v) -> {
                        if (k != null && v != null) {
                            variables.put(k.toString(), v.toString());
                        }
                    });
                }

                SendMessageRequest req = new SendMessageRequest();
                req.setTo(phone);
                req.setMessageType(Message.MessageType.TEMPLATE);
                req.setTemplate(new TemplatePayload(templateId, variables));
                req.setCampaignId(campaign.getId());

                messageService.sendMessage(req, clientId);
                successCount++;
            } catch (Exception e) {
                log.error("CAMPAIGN_SEND_FAILED campaignId={} phone={} err={}",
                        campaign.getId(), phone, e.getMessage());
            }

            // Update progress periodically (every 10 messages)
            if (successCount % 10 == 0) {
                campaign.setProcessedContacts(successCount);
                campaignRepository.save(campaign);
            }
        }

        campaign.setProcessedContacts(successCount);
        if (successCount == totalTargets) {
            campaign.setStatus(Status.COMPLETED);
        } else if (successCount == 0) {
            campaign.setStatus(Status.FAILED);
        } else {
            // Partial success — still mark as COMPLETED but log the gap
            campaign.setStatus(Status.COMPLETED);
            log.warn("CAMPAIGN_PARTIAL campaignId={} sent={}/{}", campaign.getId(), successCount, totalTargets);
        }
        campaignRepository.save(campaign);

        log.info("CAMPAIGN_DONE campaignId={} sent={}/{}", campaign.getId(), successCount, totalTargets);
    }
}
