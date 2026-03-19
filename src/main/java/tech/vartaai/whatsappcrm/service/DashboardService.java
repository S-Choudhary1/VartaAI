package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import tech.vartaai.whatsappcrm.dto.Status;
import tech.vartaai.whatsappcrm.entity.Campaign;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.repository.CampaignRepository;
import tech.vartaai.whatsappcrm.repository.ContactRepository;
import tech.vartaai.whatsappcrm.repository.MessageRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ContactRepository contactRepository;
    private final CampaignRepository campaignRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public DashboardService(ContactRepository contactRepository, 
                            CampaignRepository campaignRepository, 
                            MessageRepository messageRepository,
                            ObjectMapper objectMapper) {
        this.contactRepository = contactRepository;
        this.campaignRepository = campaignRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getStats(UUID clientId) {
        // Since repositories currently use list filters, we might need to adjust for counts.
        // For performance, direct count queries are better, but we'll use stream size for now or add count methods.
        
        long totalContacts = contactRepository.findByClient_Id(clientId).size();
        
        List<Campaign> allCampaigns = campaignRepository.findByClient_IdOrderByCreatedAtDesc(clientId);
        long activeCampaigns = allCampaigns.stream()
                .filter(c -> c.getStatus() == Status.RUNNING || c.getStatus() == Status.PENDING)
                .count();
        
        List<Message> allMessages = messageRepository.findByClient_Id(clientId);
        long messagesSent = allMessages.stream()
                .filter(m -> m.getDirection() == Message.Direction.OUTGOING && m.getStatus() == Message.Status.SENT)
                .count();
        long failedMessages = allMessages.stream()
                .filter(m -> m.getStatus() == Message.Status.FAILED)
                .count();

        // Get Recent Campaigns (Top 10 sorted by createdAt desc)
        List<Map<String, Object>> recentCampaigns = allCampaigns.stream()
                .sorted(Comparator.comparing(Campaign::getCreatedAt).reversed())
                .limit(10)
                .map(this::mapCampaignToSummary)
                .collect(Collectors.toList());

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalContacts", totalContacts);
        stats.put("activeCampaigns", activeCampaigns);
        stats.put("messagesSent", messagesSent);
        stats.put("failedMessages", failedMessages);
        stats.put("recentCampaigns", recentCampaigns);
        
        return stats;
    }

    private Map<String, Object> mapCampaignToSummary(Campaign c) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", c.getId());
        map.put("name", c.getName());
        map.put("status", c.getStatus().name());
        map.put("createdAt", c.getCreatedAt());
        
        int total = 0;
        if (c.getCsvMetadataJson() != null) {
            try {
                // Parse rudimentary JSON to get totalRows without full object mapping if possible, or map to JsonNode
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(c.getCsvMetadataJson());
                if (node.has("totalRows")) {
                    total = node.get("totalRows").asInt();
                }
            } catch (Exception e) {
                // ignore
            }
        }
        
        // Count messages for this campaign to calculate progress
        long processed = messageRepository.findByCampaignId(c.getId()).size();

        map.put("totalContacts", total);
        map.put("processedContacts", processed);
        return map;
    }
}

