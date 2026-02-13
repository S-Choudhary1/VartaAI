package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tech.vartaai.whatsappcrm.dto.SendMessageRequest;
import tech.vartaai.whatsappcrm.entity.Campaign;
import tech.vartaai.whatsappcrm.repository.CampaignRepository;
import tech.vartaai.whatsappcrm.repository.ContactRepository;
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
    private final ContactRepository contactRepository;
    private final CsvParser csvParser;
    private final ObjectMapper objectMapper;

    private final MessageService messageService;

    public CampaignService(CampaignRepository campaignRepository,
                           MessageRepository messageRepository,
                           CsvParser csvParser,
                           ObjectMapper objectMapper,
                           MessageService messageService,
                           ContactRepository contactRepository) {
        this.campaignRepository = campaignRepository;
        this.messageRepository = messageRepository;
        this.csvParser = csvParser;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.contactRepository = contactRepository;
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
            Campaign save = campaignRepository.save(c);
            for(CsvParser.Row row : rows) {
                messageService.sendMessage(
                        new SendMessageRequest(
                                row.getPhone(),
                                null,
                                null,
                                templateId,
                                row.getVariables(),
                            "META",
                                save.getId().toString()
                        ),
                        clientId
                );
            }

            return c;
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
        return campaignRepository.findByClient_IdOrderByCreatedAtDesc(clientId);
    }

    public List<Message> getCampaignMessages(UUID campaignId, UUID clientId) {
        Campaign c = getCampaign(campaignId, clientId); // Validates campaign exists and belongs to client
        return messageRepository.findByCampaignId(c.getId().toString());
    }

    /**
     * Build a CSV string for all messages belonging to a campaign, including
     * contact phone, message body (from payload), status, and user response (if any).
     */
    public String exportCampaignResponsesCsv(UUID campaignId, UUID clientId) {
        Campaign c = getCampaign(campaignId, clientId);
        List<Message> messages = messageRepository.findByCampaignId(c.getId().toString());

        StringBuilder sb = new StringBuilder();
        sb.append("contact_phone,message,status,user_response\n");

        for (Message m : messages) {
            String phone = "";
            if (m.getContactId() != null) {
                try {
                    contactRepository.findById(UUID.fromString(m.getContactId()))
                            .ifPresent(contact -> {
                                // closure needs effectively final var; use array wrapper
                            });
                } catch (IllegalArgumentException ignored) {
                    // contactId not a valid UUID; skip
                }
            }

            // Re-fetch contact properly outside lambda to keep code simple
            if (m.getContactId() != null) {
                try {
                    UUID contactUuid = UUID.fromString(m.getContactId());
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
            if (body instanceof String) {
                return (String) body;
            }
            return payloadJson;
        } catch (Exception e) {
            return "";
        }
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


