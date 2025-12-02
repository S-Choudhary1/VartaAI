package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tech.vartaai.whatsappcrm.entity.Campaign;
import tech.vartaai.whatsappcrm.repository.CampaignRepository;
import tech.vartaai.whatsappcrm.util.CsvParser;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tech.vartaai.whatsappcrm.entity.Client;

import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.repository.MessageRepository;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final MessageRepository messageRepository;
    private final CsvParser csvParser;
    private final ObjectMapper objectMapper;

    public CampaignService(CampaignRepository campaignRepository, MessageRepository messageRepository, CsvParser csvParser, ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.messageRepository = messageRepository;
        this.csvParser = csvParser;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Campaign uploadCsv(String name, UUID templateId, OffsetDateTime scheduledAt, UUID uploadedBy, MultipartFile file, UUID clientId) {
        try {
            List<CsvParser.Row> rows = csvParser.parse(file.getInputStream());
            Map<String, Object> meta = new HashMap<>();
            meta.put("originalFilename", file.getOriginalFilename());
            meta.put("totalRows", rows.size());
            meta.put("targets", rows); // Save the actual data!

            Campaign c = new Campaign();
            
            Client client = new Client();
            client.setId(clientId);
            c.setClient(client);

            c.setName(name);
            c.setTemplateId(templateId);
            c.setUploadedBy(uploadedBy);
            c.setScheduledAt(scheduledAt);
            c.setStatus(Campaign.Status.PENDING);
            c.setCsvMetadataJson(objectMapper.writeValueAsString(meta));
            return campaignRepository.save(c);
        } catch (IOException e) {
            throw new RuntimeException("Failed to process CSV", e);
        }
    }

    public Campaign getCampaign(UUID id, UUID clientId) {
        Campaign c = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));
        if (!c.getClient().getId().equals(clientId)) {
             throw new RuntimeException("Campaign not found");
        }
        return c;
    }

    public List<Campaign> getAllCampaigns(UUID clientId) {
        return campaignRepository.findByClient_Id(clientId);
    }

    public List<Message> getCampaignMessages(UUID campaignId, UUID clientId) {
        Campaign c = getCampaign(campaignId, clientId); // Validates campaign exists and belongs to client
        return messageRepository.findByCampaignId(c.getId());
    }
}


