package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import tech.vartaai.whatsappcrm.entity.FlowExecution;
import tech.vartaai.whatsappcrm.entity.FlowStepHistory;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.jobs.CampaignRunner;
import tech.vartaai.whatsappcrm.repository.CampaignRepository;
import tech.vartaai.whatsappcrm.repository.ContactRepository;
import tech.vartaai.whatsappcrm.repository.FlowExecutionRepository;
import tech.vartaai.whatsappcrm.repository.FlowStepHistoryRepository;
import tech.vartaai.whatsappcrm.repository.MessageRepository;
import tech.vartaai.whatsappcrm.util.CsvParser;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final MessageRepository messageRepository;
    private final ContactRepository contactRepository;
    private final FlowExecutionRepository flowExecutionRepository;
    private final FlowStepHistoryRepository flowStepHistoryRepository;
    private final CsvParser csvParser;
    private final ObjectMapper objectMapper;
    private final CampaignRunner campaignRunner;

    public CampaignService(CampaignRepository campaignRepository,
                           MessageRepository messageRepository,
                           CsvParser csvParser,
                           ObjectMapper objectMapper,
                           ContactRepository contactRepository,
                           CampaignRunner campaignRunner,
                           FlowExecutionRepository flowExecutionRepository,
                           FlowStepHistoryRepository flowStepHistoryRepository) {
        this.campaignRepository = campaignRepository;
        this.messageRepository = messageRepository;
        this.csvParser = csvParser;
        this.objectMapper = objectMapper;
        this.contactRepository = contactRepository;
        this.campaignRunner = campaignRunner;
        this.flowExecutionRepository = flowExecutionRepository;
        this.flowStepHistoryRepository = flowStepHistoryRepository;
    }

    /**
     * Parse CSV, create campaign with PENDING status, and return immediately.
     * The CampaignRunner scheduled job will pick it up and send messages asynchronously.
     */
    @Transactional
    public Campaign uploadCsv(String name, UUID templateId, OffsetDateTime scheduledAt, UUID uploadedBy, MultipartFile file, UUID clientId, UUID flowId) {
        log.info("CAMPAIGN_UPLOAD_CSV name='{}' templateId={} flowId={} clientId={} scheduledAt={} fileSize={}",
                name, templateId, flowId, clientId, scheduledAt,
                file != null ? file.getSize() : 0);
        Campaign campaign = new Campaign();
        campaign.setName(name);
        campaign.setTemplateId(templateId);
        campaign.setUploadedBy(uploadedBy);
        campaign.setScheduledAt(scheduledAt);
        campaign.setStatus(Status.PENDING);
        campaign.setFlowId(flowId);

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

            log.info("CAMPAIGN_CSV_PARSED campaignName='{}' rows={}", name, rows.size());
            campaignRunner.processCampaign(campaign);
            Campaign saved = campaignRepository.save(campaign);
            log.info("CAMPAIGN_CREATED campaignId={} status={} processedContacts={}",
                    saved.getId(), saved.getStatus(), saved.getProcessedContacts());
            return saved;
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
     * Export campaign responses as CSV.
     *
     * For campaigns WITH a flow attached, the output is a flat conversation:
     *   phone, message_1, response_1, message_2, response_2, ...
     * where message_1 = campaign template, response_1 = user reply,
     * message_2 = flow's next message after condition match, etc.
     *
     * For campaigns WITHOUT a flow, standard format:
     *   phone, message, message_type, status, user_response
     */
    public String exportCampaignResponsesCsv(UUID campaignId, UUID clientId) {
        Campaign c = getCampaign(campaignId, clientId);
        List<Message> campaignMessages = messageRepository.findByCampaignId(c.getId());
        boolean hasFlow = c.getFlowId() != null;

        log.info("CAMPAIGN_EXPORT campaignId={} messageCount={} hasFlow={} flowId={}",
                campaignId, campaignMessages.size(), hasFlow, c.getFlowId());

        if (hasFlow) {
            return exportFlowCampaignCsv(c, campaignMessages);
        } else {
            return exportSimpleCampaignCsv(campaignMessages);
        }
    }

    /**
     * Flow campaign export: flat conversation columns per contact.
     * Builds pairs of (message_sent, user_response) from campaign message + flow steps.
     */
    private String exportFlowCampaignCsv(Campaign c, List<Message> campaignMessages) {
        // Group campaign messages by contactId
        Map<UUID, Message> campMsgByContact = new LinkedHashMap<>();
        for (Message m : campaignMessages) {
            if (m.getContactId() != null) {
                campMsgByContact.putIfAbsent(m.getContactId(), m);
            }
        }

        // Get flow executions for this campaign
        List<FlowExecution> executions = flowExecutionRepository.findByCampaignIdOrderByStartedAtAsc(c.getId());
        Map<UUID, FlowExecution> execByContact = new LinkedHashMap<>();
        for (FlowExecution exec : executions) {
            execByContact.putIfAbsent(exec.getContactId(), exec);
        }

        // Collect all contacts
        Set<UUID> allContactIds = new LinkedHashSet<>(campMsgByContact.keySet());
        allContactIds.addAll(execByContact.keySet());

        // Build conversation pairs for each contact: [(msg1, resp1), (msg2, resp2), ...]
        // and track the max number of pairs to determine column count
        List<ContactConversation> conversations = new ArrayList<>();
        int maxPairs = 1; // at least 1 for the campaign message

        for (UUID contactId : allContactIds) {
            String phone = resolvePhone(contactId);
            List<String[]> pairs = new ArrayList<>(); // each entry = [message, response]

            // Pair 1: campaign template message + user reply
            Message campMsg = campMsgByContact.get(contactId);
            String campContent = campMsg != null ? extractMessageContent(campMsg) : "";
            String campReply = campMsg != null ? extractUserResponse(campMsg.getResponseJson()) : "";
            pairs.add(new String[]{campContent, campReply});

            // Remaining pairs from flow step history
            FlowExecution exec = execByContact.get(contactId);
            if (exec != null) {
                List<FlowStepHistory> steps = flowStepHistoryRepository
                        .findByExecutionIdOrderByCreatedAtAsc(exec.getId());

                String pendingMessage = null;
                boolean hasWaitAfterPending = false;

                for (int si = 0; si < steps.size(); si++) {
                    FlowStepHistory step = steps.get(si);

                    if (step.getAction() == FlowStepHistory.StepAction.MESSAGE_SENT && step.getMessageId() != null) {
                        // A flow message was sent — start a new pair
                        Message flowMsg = messageRepository.findById(step.getMessageId()).orElse(null);
                        if (flowMsg != null) {
                            pendingMessage = extractMessageContent(flowMsg);
                            // Check if there's a WAIT_FOR_REPLY after this message
                            // (meaning we expect a response). If not, it's a terminal message.
                            hasWaitAfterPending = false;
                            for (int sj = si + 1; sj < steps.size(); sj++) {
                                String nodeType = steps.get(sj).getNodeType();
                                if ("WAIT_FOR_REPLY".equals(nodeType)) {
                                    hasWaitAfterPending = true;
                                    break;
                                }
                                if ("END".equals(nodeType)) break;
                            }
                        }
                    }
                    if (step.getAction() == FlowStepHistory.StepAction.RESPONSE_RECEIVED && step.getResponseData() != null) {
                        // User responded — complete the pair
                        String response = extractUserResponse(step.getResponseData());
                        if (pendingMessage != null) {
                            pairs.add(new String[]{pendingMessage, response});
                            pendingMessage = null;
                        } else {
                            // Response without a preceding flow message — fill last pair's response
                            if (!pairs.isEmpty() && pairs.get(pairs.size() - 1)[1].isEmpty()) {
                                pairs.get(pairs.size() - 1)[1] = response;
                            }
                        }
                    }
                }

                // Only add trailing message if a WAIT_FOR_REPLY follows
                // (i.e., we're expecting a response). Skip terminal messages like "Thanks"
                if (pendingMessage != null && hasWaitAfterPending) {
                    pairs.add(new String[]{pendingMessage, ""});
                }
            }

            maxPairs = Math.max(maxPairs, pairs.size());
            conversations.add(new ContactConversation(phone, pairs));
        }

        // Build CSV
        StringBuilder sb = new StringBuilder();

        // Header: phone, message_1, response_1, message_2, response_2, ...
        sb.append("phone");
        for (int i = 1; i <= maxPairs; i++) {
            sb.append(",message_").append(i).append(",response_").append(i);
        }
        sb.append('\n');

        // Rows
        for (ContactConversation conv : conversations) {
            sb.append(escapeCsv(conv.phone));
            for (int i = 0; i < maxPairs; i++) {
                if (i < conv.pairs.size()) {
                    sb.append(',').append(escapeCsv(conv.pairs.get(i)[0]));
                    sb.append(',').append(escapeCsv(conv.pairs.get(i)[1]));
                } else {
                    sb.append(",\"\"").append(",\"\"");
                }
            }
            sb.append('\n');
        }

        log.info("CAMPAIGN_EXPORT_FLOW_CSV campaignId={} contacts={} maxPairs={}",
                conversations.size() > 0 ? conversations.get(0).phone : "none",
                conversations.size(), maxPairs);

        return sb.toString();
    }

    /**
     * Simple campaign export (no flow): one row per message.
     */
    private String exportSimpleCampaignCsv(List<Message> campaignMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("phone,message,message_type,status,user_response\n");

        for (Message m : campaignMessages) {
            String phone = resolvePhone(m.getContactId());
            String messageBody = extractMessageContent(m);
            String msgType = m.getMessageType() != null ? m.getMessageType().name() : "";
            String status = m.getStatus() != null ? m.getStatus().name() : "";
            String userResponse = extractUserResponse(m.getResponseJson());

            sb.append(escapeCsv(phone)).append(',')
              .append(escapeCsv(messageBody)).append(',')
              .append(escapeCsv(msgType)).append(',')
              .append(escapeCsv(status)).append(',')
              .append(escapeCsv(userResponse)).append('\n');
        }

        return sb.toString();
    }

    private record ContactConversation(String phone, List<String[]> pairs) {}

    // ═══════════════════════════════════════════════════════════════
    //  MESSAGE CONTENT RESOLVER — handles all normalized payload types
    // ═══════════════════════════════════════════════════════════════

    private String extractMessageContent(Message m) {
        if (m == null) return "";
        return extractContentFromPayload(m.getPayloadJson(), m.getMessageType());
    }

    private String extractContentFromPayload(String payloadJson, Message.MessageType messageType) {
        if (payloadJson == null || payloadJson.isEmpty()) return "";
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            String type = root.has("type") ? root.path("type").asText("") : "";

            // TEXT: { "type":"text", "text": {"body":"..."} }
            if ("text".equals(type) || messageType == Message.MessageType.TEXT) {
                JsonNode textNode = root.path("text");
                if (textNode.isObject()) {
                    return textNode.path("body").asText("");
                }
                // Fallback for flat body
                return root.path("body").asText("");
            }

            // TEMPLATE: { "type":"template", "body":"<contentJson>", "variables":{...}, "templateName":"..." }
            if ("template".equals(type) || messageType == Message.MessageType.TEMPLATE) {
                String bodyRaw = root.path("body").asText("");
                JsonNode vars = root.path("variables");

                // body may be a JSON components array — parse it to extract BODY text
                String resolvedBody = resolveTemplateBody(bodyRaw, vars);
                if (!resolvedBody.isEmpty()) {
                    return resolvedBody;
                }
                return "Template message";
            }

            // INTERACTIVE: { "type":"interactive", "interactive":{"type":"button","body":{"text":"..."}} }
            if ("interactive".equals(type) || messageType == Message.MessageType.INTERACTIVE) {
                JsonNode interactive = root.path("interactive");
                String bodyText = interactive.path("body").path("text").asText("");
                if (!bodyText.isEmpty()) return bodyText;
                return "Interactive message";
            }

            // MEDIA types (image, video, audio, document, sticker)
            for (String mediaType : List.of("image", "video", "audio", "document", "sticker")) {
                if (mediaType.equals(type) || root.has(mediaType)) {
                    JsonNode media = root.path(mediaType);
                    String caption = media.path("caption").asText("");
                    String filename = media.path("filename").asText("");
                    if (!caption.isEmpty()) return "[" + mediaType.toUpperCase() + "] " + caption;
                    if (!filename.isEmpty()) return "[" + mediaType.toUpperCase() + "] " + filename;
                    return "[" + mediaType.toUpperCase() + "]";
                }
            }

            // LOCATION
            if ("location".equals(type) || root.has("location")) {
                JsonNode loc = root.path("location");
                String name = loc.path("name").asText("");
                String address = loc.path("address").asText("");
                if (!name.isEmpty()) return "[LOCATION] " + name + (address.isEmpty() ? "" : ", " + address);
                return "[LOCATION] " + loc.path("latitude").asText("") + "," + loc.path("longitude").asText("");
            }

            // CONTACTS
            if ("contacts".equals(type) || root.has("contacts")) {
                return "[CONTACTS]";
            }

            // REACTION
            if ("reaction".equals(type)) {
                return root.path("reaction").path("emoji").asText("Reaction");
            }

            // Fallback: try flat body field (old format)
            String body = root.path("body").asText("");
            if (!body.isEmpty()) {
                return resolveTemplateBody(body, root.path("variables"));
            }

            return "";
        } catch (Exception e) {
            log.warn("Failed to extract message content: {}", e.getMessage());
            return "";
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  USER RESPONSE RESOLVER — handles all normalized response types
    // ═══════════════════════════════════════════════════════════════

    private String extractUserResponse(String responseJson) {
        if (responseJson == null || responseJson.isEmpty()) return "";
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String type = root.path("type").asText("");

            switch (type) {
                case "text":
                    return root.path("text").asText("");

                case "button": {
                    String text = root.path("text").asText("");
                    String payload = root.path("payload").asText("");
                    if (!text.isEmpty() && !payload.isEmpty() && !text.equals(payload)) {
                        return text + " (" + payload + ")";
                    }
                    return !text.isEmpty() ? text : payload;
                }

                case "interactive": {
                    String id = root.path("id").asText("");
                    String title = root.path("title").asText("");
                    String desc = root.path("description").asText("");
                    StringBuilder sb2 = new StringBuilder();
                    if (!title.isEmpty()) sb2.append(title);
                    if (!id.isEmpty() && !id.equals(title)) sb2.append(" (").append(id).append(")");
                    if (!desc.isEmpty()) sb2.append(" - ").append(desc);
                    return sb2.length() > 0 ? sb2.toString() : "Interactive reply";
                }

                case "reaction":
                    return root.path("emoji").asText("Reaction");

                case "location": {
                    String name = root.path("name").asText("");
                    String address = root.path("address").asText("");
                    String lat = root.has("latitude") ? String.valueOf(root.path("latitude").asDouble()) : "";
                    String lon = root.has("longitude") ? String.valueOf(root.path("longitude").asDouble()) : "";
                    String coords = (!lat.isEmpty() && !lon.isEmpty()) ? lat + "," + lon : "";
                    if (!name.isEmpty()) return name + (!address.isEmpty() ? " - " + address : "") + (coords.isEmpty() ? "" : " (" + coords + ")");
                    if (!address.isEmpty()) return address + (coords.isEmpty() ? "" : " (" + coords + ")");
                    return coords;
                }

                case "image": case "video": case "audio": case "document": case "sticker": {
                    String caption = root.path("caption").asText("");
                    String filename = root.path("filename").asText("");
                    if (!caption.isEmpty()) return "[" + type.toUpperCase() + "] " + caption;
                    if (!filename.isEmpty()) return "[" + type.toUpperCase() + "] " + filename;
                    return "[" + type.toUpperCase() + "]";
                }

                case "contacts":
                    return "[CONTACTS]";

                case "order":
                    return "[ORDER] " + root.path("text").asText("");

                default:
                    // For unknown types, return a readable summary
                    if (root.has("text")) return root.path("text").asText("");
                    if (root.has("raw")) return "[" + type.toUpperCase() + "]";
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Resolve template body text from contentJson.
     * contentJson can be:
     *   1. A JSON array of components: [{"type":"BODY","text":"Hello {{1}}"},{"type":"FOOTER",...}]
     *   2. Plain text with {{var}} placeholders: "Hello {{name}}"
     * Variables are substituted from the vars JsonNode.
     */
    private String resolveTemplateBody(String bodyRaw, JsonNode vars) {
        if (bodyRaw == null || bodyRaw.isEmpty()) return "";

        String bodyText = "";

        // Try parsing as JSON components array
        try {
            JsonNode parsed = objectMapper.readTree(bodyRaw);
            if (parsed.isArray()) {
                // Components format: [{type:"HEADER",text:".."},{type:"BODY",text:".."},{type:"FOOTER",text:".."}]
                for (JsonNode comp : parsed) {
                    if ("BODY".equals(comp.path("type").asText(""))) {
                        bodyText = comp.path("text").asText("");
                        break;
                    }
                }
            } else if (parsed.isTextual()) {
                bodyText = parsed.asText("");
            }
        } catch (Exception e) {
            // Not JSON — treat as plain text
            bodyText = bodyRaw;
        }

        if (bodyText.isEmpty()) {
            bodyText = bodyRaw;
        }

        // Substitute variables: {{1}}, {{2}} or {{name}}, {{phone}}
        if (vars != null && vars.isObject()) {
            var it = vars.fields();
            while (it.hasNext()) {
                var entry = it.next();
                bodyText = bodyText.replace("{{" + entry.getKey() + "}}", entry.getValue().asText(""));
            }
        }

        return bodyText;
    }

    private String resolvePhone(UUID contactId) {
        if (contactId == null) return "";
        try {
            return contactRepository.findById(contactId)
                    .map(tech.vartaai.whatsappcrm.entity.Contact::getPhone)
                    .orElse("");
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


