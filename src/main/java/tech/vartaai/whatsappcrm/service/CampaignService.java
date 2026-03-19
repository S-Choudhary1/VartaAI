package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tech.vartaai.whatsappcrm.dto.CampaignDto;
import tech.vartaai.whatsappcrm.dto.Status;
import tech.vartaai.whatsappcrm.entity.Campaign;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.repository.CampaignRepository;
import tech.vartaai.whatsappcrm.repository.ContactRepository;
import tech.vartaai.whatsappcrm.repository.MessageRepository;
import tech.vartaai.whatsappcrm.util.CsvParser;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final MessageRepository messageRepository;
    private final ContactRepository contactRepository;
    private final CsvParser csvParser;
    private final ObjectMapper objectMapper;

    public CampaignService(CampaignRepository campaignRepository,
                           MessageRepository messageRepository,
                           CsvParser csvParser,
                           ObjectMapper objectMapper,
                           ContactRepository contactRepository) {
        this.campaignRepository = campaignRepository;
        this.messageRepository = messageRepository;
        this.csvParser = csvParser;
        this.objectMapper = objectMapper;
        this.contactRepository = contactRepository;
    }

    /**
     * Parse CSV, create campaign with PENDING status, and return immediately.
     * The CampaignRunner scheduled job will pick it up and send messages asynchronously.
     */
    @Transactional
    public Campaign uploadCsv(String name, UUID templateId, OffsetDateTime scheduledAt, UUID uploadedBy, MultipartFile file, UUID clientId) {
        Campaign campaign = new Campaign();
        campaign.setName(name);
        campaign.setTemplateId(templateId);
        campaign.setUploadedBy(uploadedBy);
        campaign.setScheduledAt(scheduledAt);
        campaign.setStatus(Status.PENDING);

        try {
            List<CsvParser.Row> rows = csvParser.parse(file.getInputStream());

            Map<String, Object> meta = new HashMap<>();
            meta.put("originalFilename", file.getOriginalFilename());
            meta.put("totalRows", rows.size());
            meta.put("targets", rows);

            Client client = new Client();
            client.setId(clientId);
            campaign.setClient(client);
            campaign.setTotalContacts(rows.size());
            campaign.setProcessedContacts(0);
            campaign.setCsvMetadataJson(objectMapper.writeValueAsString(meta));

            return campaignRepository.save(campaign);
        } catch (IOException e) {
            campaign.setStatus(Status.FAILED);
            campaignRepository.save(campaign);
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

    public List<CampaignDto> getAllCampaigns(UUID clientId) {
        try {
            List<Campaign> campaignList = campaignRepository.findByClient_IdOrderByCreatedAtDesc(clientId);
            List<CampaignDto> resultList = new ArrayList<>();
            for (Campaign campaign : campaignList) {

                CampaignDto dto = campaign.toDto();
                resultList.add(dto);
            }
            return resultList;
        } catch (Exception e) {
            log.error("Exception in getAllCampaigns {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public Page<CampaignDto> getAllCampaigns(UUID clientId, Pageable pageable) {
        try {
            return campaignRepository.findByClient_IdOrderByCreatedAtDesc(clientId, pageable)
                    .map(Campaign::toDto);
        } catch (Exception e) {
            log.error("Exception in getAllCampaigns (paginated) {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public List<Message> getCampaignMessages(UUID campaignId, UUID clientId) {
        Campaign c = getCampaign(campaignId, clientId); // Validates campaign exists and belongs to client
        return messageRepository.findByCampaignId(c.getId());
    }

    /**
     * Build a CSV string for all messages belonging to a campaign, including
     * contact phone, message body (from payload), status, and user response (if any).
     */
    public String exportCampaignResponsesCsv(UUID campaignId, UUID clientId) {
        Campaign c = getCampaign(campaignId, clientId);
        List<Message> messages = messageRepository.findByCampaignId(c.getId());

        StringBuilder sb = new StringBuilder();
        sb.append("contact_phone,message,status,user_response\n");

        for (Message m : messages) {
            String phone = "";
            if (m.getContactId() != null) {
                try {
                    UUID contactUuid = m.getContactId();
                    phone = contactRepository.findById(contactUuid)
                            .map(tech.vartaai.whatsappcrm.entity.Contact::getPhone)
                            .orElse("");
                } catch (IllegalArgumentException ignored) {
                }
            }

            String messageBody = extractBodyFromPayload(m.getPayloadJson());
            String status = m.getStatus() != null ? m.getStatus().name() : "";
            String userResponse = extractUserResponse(m.getResponseJson());

            sb.append(escapeCsv(phone)).append(',')
              .append(escapeCsv(messageBody)).append(',')
              .append(escapeCsv(status)).append(',')
              .append(escapeCsv(userResponse)).append('\n');
        }

        return sb.toString();
    }

    private String extractBodyFromPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isEmpty()) return "";
        try {
            Map<?, ?> map = objectMapper.readValue(payloadJson, Map.class);
            Object body = map.get("body");
            Map<String, String> varibales = (Map<String, String>) map.get("variables");
            if (body instanceof String) {
                return fillTemplate((String)body, varibales);
            }
            return payloadJson;
        } catch (Exception e) {
            return "";
        }
    }
    private String fillTemplate(String template, Map<String, String> values) {
        String result = template;

        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        return result;
    }

    private String extractUserResponse(String responseJson) {
        if (responseJson == null || responseJson.isEmpty()) return "";
        try {
            Map<?, ?> map = objectMapper.readValue(responseJson, Map.class);
            Object type = map.get("type");
            if ("text".equals(type)) {
                Object text = map.get("text");
                return text != null ? text.toString() : "";
            }
            if ("button".equals(type)) {
                Object text = map.get("text");
                Object payload = map.get("payload");
                if (text != null && payload != null && !text.equals(payload)) {
                    return text + " (" + payload + ")";
                }
                return text != null ? text.toString() : (payload != null ? payload.toString() : "");
            }
            if ("location".equals(type)) {
                Object latitude = map.get("latitude");
                Object longitude = map.get("longitude");
                Object name = map.get("name");
                Object address = map.get("address");

                String latLng = (latitude != null || longitude != null)
                        ? String.valueOf(latitude) + "," + String.valueOf(longitude)
                        : "";

                String label = name != null ? name.toString() : "";
                String addr = address != null ? address.toString() : "";

                if (!label.isBlank() && !addr.isBlank()) {
                    return label + " - " + addr + " (" + latLng + ")";
                }
                if (!label.isBlank()) {
                    return label + " (" + latLng + ")";
                }
                if (!addr.isBlank()) {
                    return addr + " (" + latLng + ")";
                }
                return latLng;
            }
            return responseJson;
        } catch (Exception e) {
            return "";
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        String v = value.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }
}


