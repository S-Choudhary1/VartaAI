package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.vartaai.whatsappcrm.dto.SendMessageRequest;
import tech.vartaai.whatsappcrm.dto.message.InteractivePayload;
import tech.vartaai.whatsappcrm.dto.message.TemplatePayload;
import tech.vartaai.whatsappcrm.dto.message.TextPayload;
import tech.vartaai.whatsappcrm.entity.*;
import tech.vartaai.whatsappcrm.exception.ApiException;
import tech.vartaai.whatsappcrm.integration.provider.SendResponse;
import tech.vartaai.whatsappcrm.repository.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
public class FlowEngineService {

    private final FlowRepository flowRepository;
    private final FlowExecutionRepository flowExecutionRepository;
    private final FlowStepHistoryRepository flowStepHistoryRepository;
    private final MessageService messageService;
    private final ContactRepository contactRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public FlowEngineService(FlowRepository flowRepository,
                             FlowExecutionRepository flowExecutionRepository,
                             FlowStepHistoryRepository flowStepHistoryRepository,
                             MessageService messageService,
                             ContactRepository contactRepository,
                             MessageRepository messageRepository,
                             ObjectMapper objectMapper) {
        this.flowRepository = flowRepository;
        this.flowExecutionRepository = flowExecutionRepository;
        this.flowStepHistoryRepository = flowStepHistoryRepository;
        this.messageService = messageService;
        this.contactRepository = contactRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public void processIncomingMessage(Contact contact, Client client,
                                       String responseJson, String providerMessageId) {
        try {
            UUID contactId = contact.getId();
            UUID clientId = client.getId();

            log.info("FLOW_PROCESS_INCOMING contactId={} clientId={} providerMsgId={} responseType={}",
                    contactId, clientId, providerMessageId, extractTypeFromResponse(responseJson));

            List<FlowExecution> activeExecs = flowExecutionRepository
                    .findActiveExecutionsForContact(contactId, clientId);

            log.info("FLOW_ACTIVE_EXECS_FOUND contactId={} count={} statuses={}",
                    contactId, activeExecs.size(),
                    activeExecs.stream().map(e -> e.getId() + ":" + e.getStatus()).toList());

            if (!activeExecs.isEmpty()) {
                activeExecs.sort((a, b) -> {
                    if (a.getStatus() == FlowExecution.ExecutionStatus.WAITING) return -1;
                    if (b.getStatus() == FlowExecution.ExecutionStatus.WAITING) return 1;
                    return 0;
                });

                FlowExecution exec = activeExecs.get(0);

                if (exec.getStatus() == FlowExecution.ExecutionStatus.WAITING) {
                    log.info("FLOW_RESUME_WAITING execId={} flowId={} contactId={} currentNode={}",
                            exec.getId(), exec.getFlow().getId(), contactId, exec.getCurrentNodeId());
                    handleWaitingExecution(exec, contact, client, responseJson);
                    return;
                }

                log.info("FLOW_ACTIVE_SKIP execId={} status={} contactId={} — not resuming, not starting keyword flow",
                        exec.getId(), exec.getStatus(), contactId);
                return;
            }

            // No active execution → try keyword triggers
            String incomingText = extractTextFromResponse(responseJson);
            log.info("FLOW_NO_ACTIVE_EXEC contactId={} — checking keyword triggers, text='{}'", contactId, incomingText);

            if (incomingText == null || incomingText.isBlank()) {
                log.info("FLOW_NO_TEXT contactId={} — no text extracted, skipping keyword check", contactId);
                return;
            }

            List<Flow> activeFlows = flowRepository.findByClient_IdAndStatusAndTriggerType(
                    clientId, Flow.FlowStatus.ACTIVE, Flow.TriggerType.KEYWORD);
            log.info("FLOW_KEYWORD_FLOWS_FOUND clientId={} count={}", clientId, activeFlows.size());

            for (Flow flow : activeFlows) {
                if (matchesKeyword(incomingText, flow.getTriggerKeywords())) {
                    log.info("FLOW_KEYWORD_MATCH flowId={} flowName={} contactId={} matchedText='{}' keywords='{}'",
                            flow.getId(), flow.getName(), contactId, incomingText, flow.getTriggerKeywords());
                    startFlowForContact(flow, contact, null);
                    return;
                }
            }

            log.info("FLOW_NO_KEYWORD_MATCH contactId={} text='{}' — no flow triggered", contactId, incomingText);
        } catch (Exception e) {
            log.error("FLOW_ENGINE_ERROR contactId={} err={}", contact.getId(), e.getMessage(), e);
        }
    }

    @Transactional
    public FlowExecution startFlowForContact(Flow flow, Contact contact, UUID campaignId) {
        flow = flowRepository.findById(flow.getId()).orElse(flow);
        UUID clientId = flow.getClient().getId();

        String mode = campaignId != null ? "CAMPAIGN" : "CHATBOT";
        log.info("FLOW_START_REQUEST flowId={} flowName={} contactId={} phone={} campaignId={} mode={}",
                flow.getId(), flow.getName(), contact.getId(), contact.getPhone(), campaignId, mode);

        // For non-campaign flows, prevent duplicate active executions
        if (campaignId == null) {
            List<FlowExecution> existing = flowExecutionRepository
                    .findActiveExecutionsForContact(contact.getId(), clientId);
            for (FlowExecution ex : existing) {
                if (ex.getFlow().getId().equals(flow.getId())) {
                    log.info("FLOW_ALREADY_ACTIVE flowId={} contactId={} existingExecId={} — returning existing",
                            flow.getId(), contact.getId(), ex.getId());
                    return ex;
                }
            }
        }

        // Validate definition
        JsonNode definition = parseDefinition(flow.getDefinitionJson());
        JsonNode startNode = findNodeByType(definition, "START");
        if (startNode == null) {
            log.error("FLOW_NO_START_NODE flowId={} — cannot start flow, no START node in definition", flow.getId());
            return null;
        }

        // Create execution
        FlowExecution exec = new FlowExecution();
        exec.setFlow(flow);
        exec.setClientId(clientId);
        exec.setContactId(contact.getId());
        exec.setCampaignId(campaignId);
        exec.setStatus(FlowExecution.ExecutionStatus.ACTIVE);
        flowExecutionRepository.save(exec);

        String startNodeId = startNode.path("id").asText();
        exec.setCurrentNodeId(startNodeId);
        flowExecutionRepository.save(exec);
        recordStep(exec.getId(), startNodeId, "START", FlowStepHistory.StepAction.ENTERED, null, null, null);

        log.info("FLOW_EXEC_CREATED execId={} flowId={} contactId={} mode={} startNode={}",
                exec.getId(), flow.getId(), contact.getId(), mode, startNodeId);

        // Walk from START to the first real node
        String nextNodeId = findNextNodeId(definition, startNodeId, null);
        log.info("FLOW_START_NEXT execId={} startNode={} → nextNode={}", exec.getId(), startNodeId, nextNodeId);

        if (nextNodeId != null) {
            if (campaignId != null) {
                log.info("FLOW_CAMPAIGN_MODE execId={} — skipping SEND_MESSAGE nodes, advancing to WAIT_FOR_REPLY", exec.getId());
                advanceCampaignFlow(exec, definition, nextNodeId, contact, flow.getClient());
            } else {
                log.info("FLOW_CHATBOT_MODE execId={} — executing all nodes normally", exec.getId());
                executeNode(exec, definition, nextNodeId, contact, flow.getClient());
            }
        } else {
            log.warn("FLOW_NO_NEXT_AFTER_START execId={} — completing immediately", exec.getId());
            completeExecution(exec);
        }

        log.info("FLOW_START_DONE execId={} finalStatus={} currentNode={}",
                exec.getId(), exec.getStatus(), exec.getCurrentNodeId());
        return exec;
    }

    @Transactional
    public void handleTimedOutExecution(FlowExecution exec) {
        exec = flowExecutionRepository.findById(exec.getId()).orElse(exec);
        log.info("FLOW_TIMEOUT_PROCESSING execId={} currentNode={}", exec.getId(), exec.getCurrentNodeId());
        exec.setStatus(FlowExecution.ExecutionStatus.TIMED_OUT);
        exec.setCompletedAt(OffsetDateTime.now());
        flowExecutionRepository.save(exec);
        recordStep(exec.getId(), exec.getCurrentNodeId(), "WAIT_FOR_REPLY",
                FlowStepHistory.StepAction.FAILED, null, null, null);
        log.info("FLOW_TIMED_OUT execId={} — execution marked as TIMED_OUT", exec.getId());
    }

    // ═══════════════════════════════════════════════════════════════
    //  CAMPAIGN MODE
    // ═══════════════════════════════════════════════════════════════

    private void advanceCampaignFlow(FlowExecution exec, JsonNode definition,
                                     String nodeId, Contact contact, Client client) {
        JsonNode node = findNodeById(definition, nodeId);
        if (node == null) {
            log.warn("FLOW_CAMPAIGN_ADVANCE_NODE_NOT_FOUND execId={} nodeId={} — completing", exec.getId(), nodeId);
            completeExecution(exec);
            return;
        }

        String type = node.path("type").asText();
        exec.setCurrentNodeId(nodeId);
        flowExecutionRepository.save(exec);

        log.info("FLOW_CAMPAIGN_ADVANCE execId={} nodeId={} nodeType={}", exec.getId(), nodeId, type);

        switch (type) {
            case "SEND_MESSAGE" -> {
                log.info("FLOW_CAMPAIGN_SKIP_SEND execId={} nodeId={} — campaign already sent template, skipping",
                        exec.getId(), nodeId);
                recordStep(exec.getId(), nodeId, "SEND_MESSAGE",
                        FlowStepHistory.StepAction.ENTERED, null, null, null);
                String nextNodeId = findNextNodeId(definition, nodeId, null);
                log.info("FLOW_CAMPAIGN_SKIP_NEXT execId={} skippedNode={} → nextNode={}", exec.getId(), nodeId, nextNodeId);
                if (nextNodeId != null) {
                    advanceCampaignFlow(exec, definition, nextNodeId, contact, client);
                } else {
                    log.warn("FLOW_CAMPAIGN_NO_NEXT_AFTER_SKIP execId={} — completing", exec.getId());
                    completeExecution(exec);
                }
            }
            case "WAIT_FOR_REPLY" -> {
                log.info("FLOW_CAMPAIGN_WAIT_REACHED execId={} nodeId={} — entering WAITING state for user reply",
                        exec.getId(), nodeId);
                recordStep(exec.getId(), nodeId, type, FlowStepHistory.StepAction.ENTERED, null, null, null);
                executeWaitForReply(exec, node);
            }
            default -> {
                log.info("FLOW_CAMPAIGN_OTHER_NODE execId={} nodeId={} type={} — executing normally", exec.getId(), nodeId, type);
                recordStep(exec.getId(), nodeId, type, FlowStepHistory.StepAction.ENTERED, null, null, null);
                switch (type) {
                    case "CONDITION" -> evaluateCondition(exec, definition, node, null, contact, client);
                    case "END" -> completeExecution(exec);
                    default -> {
                        String nextNodeId = findNextNodeId(definition, nodeId, null);
                        if (nextNodeId != null) advanceCampaignFlow(exec, definition, nextNodeId, contact, client);
                        else completeExecution(exec);
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CHATBOT MODE
    // ═══════════════════════════════════════════════════════════════

    private void handleWaitingExecution(FlowExecution exec, Contact contact,
                                        Client client, String responseJson) {
        Flow flow = exec.getFlow();
        JsonNode definition = parseDefinition(flow.getDefinitionJson());
        String waitNodeId = exec.getCurrentNodeId();

        log.info("FLOW_HANDLE_WAITING execId={} flowId={} waitNode={} responseText='{}' responseId='{}'",
                exec.getId(), flow.getId(), waitNodeId,
                extractTextFromResponse(responseJson), extractIdFromResponse(responseJson));

        exec.setResumeAfter(null);
        recordStep(exec.getId(), waitNodeId, "WAIT_FOR_REPLY",
                FlowStepHistory.StepAction.RESPONSE_RECEIVED, null, responseJson, null);

        exec.setStatus(FlowExecution.ExecutionStatus.ACTIVE);
        flowExecutionRepository.save(exec);

        String nextNodeId = findNextNodeId(definition, waitNodeId, null);
        log.info("FLOW_AFTER_WAIT execId={} waitNode={} → nextNode={}", exec.getId(), waitNodeId, nextNodeId);

        if (nextNodeId == null) {
            log.info("FLOW_NO_NEXT_AFTER_WAIT execId={} — completing", exec.getId());
            completeExecution(exec);
            return;
        }

        JsonNode nextNode = findNodeById(definition, nextNodeId);
        if (nextNode == null) {
            log.warn("FLOW_NEXT_NODE_NOT_FOUND execId={} nextNodeId={} — completing", exec.getId(), nextNodeId);
            completeExecution(exec);
            return;
        }

        String nextType = nextNode.path("type").asText();
        log.info("FLOW_NEXT_NODE_TYPE execId={} nextNode={} nextType={}", exec.getId(), nextNodeId, nextType);

        if ("CONDITION".equals(nextType)) {
            evaluateCondition(exec, definition, nextNode, responseJson, contact, client);
        } else {
            executeNode(exec, definition, nextNodeId, contact, client);
        }
    }

    private void executeNode(FlowExecution exec, JsonNode definition, String nodeId,
                              Contact contact, Client client) {
        JsonNode node = findNodeById(definition, nodeId);
        if (node == null) {
            log.warn("FLOW_NODE_NOT_FOUND execId={} nodeId={} — completing", exec.getId(), nodeId);
            completeExecution(exec);
            return;
        }

        String type = node.path("type").asText();
        exec.setCurrentNodeId(nodeId);
        flowExecutionRepository.save(exec);
        recordStep(exec.getId(), nodeId, type, FlowStepHistory.StepAction.ENTERED, null, null, null);

        log.info("FLOW_EXEC_NODE execId={} nodeId={} nodeType={}", exec.getId(), nodeId, type);

        switch (type) {
            case "SEND_MESSAGE" -> executeSendMessage(exec, definition, node, contact, client);
            case "WAIT_FOR_REPLY" -> executeWaitForReply(exec, node);
            case "CONDITION" -> evaluateCondition(exec, definition, node, null, contact, client);
            case "END" -> completeExecution(exec);
            default -> {
                log.warn("FLOW_UNKNOWN_NODE_TYPE execId={} type={} nodeId={} — skipping to next", exec.getId(), type, nodeId);
                String nextId = findNextNodeId(definition, nodeId, null);
                if (nextId != null) executeNode(exec, definition, nextId, contact, client);
                else completeExecution(exec);
            }
        }
    }

    private void executeSendMessage(FlowExecution exec, JsonNode definition, JsonNode node,
                                    Contact contact, Client client) {
        String nodeId = node.path("id").asText();
        JsonNode data = node.path("data");
        String messageType = data.path("messageType").asText("TEXT");

        log.info("FLOW_SEND_MESSAGE execId={} nodeId={} messageType={} to={}",
                exec.getId(), nodeId, messageType, contact.getPhone());

        try {
            SendMessageRequest req = new SendMessageRequest();
            req.setTo(contact.getPhone());

            switch (messageType) {
                case "TEXT" -> {
                    req.setMessageType(Message.MessageType.TEXT);
                    String body = data.path("text").path("body").asText("");
                    req.setText(new TextPayload(body, null));
                    log.info("FLOW_SEND_TEXT execId={} nodeId={} bodyLength={}", exec.getId(), nodeId, body.length());
                }
                case "TEMPLATE" -> {
                    req.setMessageType(Message.MessageType.TEMPLATE);
                    String templateIdStr = data.path("template").path("templateId").asText(null);
                    if (templateIdStr != null) {
                        Map<String, String> vars = new HashMap<>();
                        JsonNode varsNode = data.path("template").path("variables");
                        if (varsNode.isObject()) {
                            varsNode.fields().forEachRemaining(e -> vars.put(e.getKey(), e.getValue().asText("")));
                        }
                        req.setTemplate(new TemplatePayload(UUID.fromString(templateIdStr), vars));
                        log.info("FLOW_SEND_TEMPLATE execId={} nodeId={} templateId={} vars={}",
                                exec.getId(), nodeId, templateIdStr, vars.size());
                    }
                }
                case "INTERACTIVE" -> {
                    req.setMessageType(Message.MessageType.INTERACTIVE);
                    JsonNode interactiveNode = data.path("interactive");
                    if (interactiveNode.isObject()) {
                        req.setInteractive(objectMapper.treeToValue(interactiveNode, InteractivePayload.class));
                        log.info("FLOW_SEND_INTERACTIVE execId={} nodeId={} interactiveType={}",
                                exec.getId(), nodeId, interactiveNode.path("type").asText(""));
                    }
                }
                default -> {
                    req.setMessageType(Message.MessageType.TEXT);
                    req.setText(new TextPayload(data.path("text").path("body").asText(""), null));
                    log.info("FLOW_SEND_DEFAULT_TEXT execId={} nodeId={}", exec.getId(), nodeId);
                }
            }

            SendResponse resp = messageService.sendMessage(req, client.getId());

            UUID messageId = null;
            if (resp != null && resp.getId() != null) {
                messageId = UUID.fromString(resp.getId());
                Message msg = messageRepository.findById(messageId).orElse(null);
                if (msg != null) {
                    msg.setFlowExecutionId(exec.getId());
                    messageRepository.save(msg);
                }
            }

            log.info("FLOW_SEND_SUCCESS execId={} nodeId={} messageId={} providerMsgId={}",
                    exec.getId(), nodeId, messageId,
                    resp != null ? resp.getProviderMessageId() : "null");

            recordStep(exec.getId(), nodeId, "SEND_MESSAGE",
                    FlowStepHistory.StepAction.MESSAGE_SENT, messageId, null, null);

            String nextNodeId = findNextNodeId(definition, nodeId, null);
            log.info("FLOW_SEND_NEXT execId={} nodeId={} → nextNode={}", exec.getId(), nodeId, nextNodeId);

            if (nextNodeId != null) {
                executeNode(exec, definition, nextNodeId, contact, client);
            } else {
                log.info("FLOW_SEND_NO_NEXT execId={} nodeId={} — completing", exec.getId(), nodeId);
                completeExecution(exec);
            }

        } catch (Exception e) {
            log.error("FLOW_SEND_FAILED execId={} nodeId={} messageType={} to={} err={}",
                    exec.getId(), nodeId, messageType, contact.getPhone(), e.getMessage(), e);
            recordStep(exec.getId(), nodeId, "SEND_MESSAGE",
                    FlowStepHistory.StepAction.FAILED, null, null, null);
            exec.setStatus(FlowExecution.ExecutionStatus.FAILED);
            exec.setCompletedAt(OffsetDateTime.now());
            flowExecutionRepository.save(exec);
        }
    }

    private void executeWaitForReply(FlowExecution exec, JsonNode node) {
        exec.setStatus(FlowExecution.ExecutionStatus.WAITING);
        int timeoutSeconds = node.path("data").path("timeoutSeconds").asInt(0);
        if (timeoutSeconds > 0) {
            exec.setResumeAfter(OffsetDateTime.now().plusSeconds(timeoutSeconds));
        }
        flowExecutionRepository.save(exec);
        log.info("FLOW_WAITING execId={} nodeId={} status=WAITING timeoutSec={} resumeAfter={}",
                exec.getId(), node.path("id").asText(), timeoutSeconds,
                exec.getResumeAfter() != null ? exec.getResumeAfter() : "none");
    }

    private void evaluateCondition(FlowExecution exec, JsonNode definition, JsonNode condNode,
                                   String responseJson, Contact contact, Client client) {
        String nodeId = condNode.path("id").asText();
        exec.setCurrentNodeId(nodeId);
        flowExecutionRepository.save(exec);

        JsonNode conditions = condNode.path("data").path("conditions");
        String responseText = extractTextFromResponse(responseJson);
        String responseId = extractIdFromResponse(responseJson);
        int condCount = conditions.isArray() ? conditions.size() : 0;

        log.info("FLOW_CONDITION_EVAL execId={} nodeId={} conditionCount={} responseText='{}' responseId='{}'",
                exec.getId(), nodeId, condCount, responseText, responseId);

        String matchedConditionId = null;
        String defaultConditionId = null;

        if (conditions.isArray()) {
            for (JsonNode cond : conditions) {
                String condId = cond.path("id").asText();
                String matchType = cond.path("matchType").asText("");
                String value = cond.path("value").asText("");
                String label = cond.path("label").asText("");

                if ("DEFAULT".equals(matchType)) {
                    defaultConditionId = condId;
                    log.debug("FLOW_CONDITION_DEFAULT execId={} condId={} label='{}'", exec.getId(), condId, label);
                    continue;
                }

                boolean matched = switch (matchType) {
                    case "EXACT" -> responseText != null && responseText.equalsIgnoreCase(value);
                    case "CONTAINS" -> responseText != null &&
                            responseText.toLowerCase().contains(value.toLowerCase());
                    case "BUTTON_ID" -> value.equals(responseId);
                    case "LIST_ID" -> value.equals(responseId);
                    case "REGEX" -> {
                        try {
                            yield responseText != null && Pattern.compile(value, Pattern.CASE_INSENSITIVE)
                                    .matcher(responseText).find();
                        } catch (Exception e) {
                            log.warn("FLOW_CONDITION_REGEX_ERROR execId={} condId={} pattern='{}' err={}",
                                    exec.getId(), condId, value, e.getMessage());
                            yield false;
                        }
                    }
                    default -> false;
                };

                log.info("FLOW_CONDITION_CHECK execId={} condId={} label='{}' matchType={} value='{}' matched={}",
                        exec.getId(), condId, label, matchType, value, matched);

                if (matched) {
                    matchedConditionId = condId;
                    break;
                }
            }
        }

        if (matchedConditionId == null && defaultConditionId != null) {
            matchedConditionId = defaultConditionId;
            log.info("FLOW_CONDITION_FALLBACK execId={} nodeId={} — using DEFAULT condId={}", exec.getId(), nodeId, defaultConditionId);
        }

        log.info("FLOW_CONDITION_RESULT execId={} nodeId={} matchedCondId={}",
                exec.getId(), nodeId, matchedConditionId != null ? matchedConditionId : "NONE");

        recordStep(exec.getId(), nodeId, "CONDITION",
                FlowStepHistory.StepAction.CONDITION_EVALUATED, null, responseJson, matchedConditionId);

        if (matchedConditionId != null) {
            String nextNodeId = findNextNodeId(definition, nodeId, matchedConditionId);
            log.info("FLOW_CONDITION_ROUTE execId={} condId={} → nextNode={}", exec.getId(), matchedConditionId, nextNodeId);
            if (nextNodeId != null) {
                executeNode(exec, definition, nextNodeId, contact, client);
            } else {
                log.warn("FLOW_NO_EDGE_FOR_CONDITION execId={} condId={} — no edge found, completing", exec.getId(), matchedConditionId);
                completeExecution(exec);
            }
        } else {
            log.warn("FLOW_NO_CONDITION_MATCH execId={} nodeId={} — no condition matched and no DEFAULT, completing",
                    exec.getId(), nodeId);
            completeExecution(exec);
        }
    }

    private void completeExecution(FlowExecution exec) {
        exec.setStatus(FlowExecution.ExecutionStatus.COMPLETED);
        exec.setCompletedAt(OffsetDateTime.now());
        flowExecutionRepository.save(exec);
        recordStep(exec.getId(), exec.getCurrentNodeId(), "END",
                FlowStepHistory.StepAction.COMPLETED, null, null, null);
        log.info("FLOW_COMPLETED execId={} currentNode={} — execution finished successfully", exec.getId(), exec.getCurrentNodeId());
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    private JsonNode parseDefinition(String definitionJson) {
        try {
            return objectMapper.readTree(definitionJson);
        } catch (Exception e) {
            log.error("FLOW_PARSE_ERROR definitionLength={} err={}",
                    definitionJson != null ? definitionJson.length() : 0, e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FLOW_PARSE_ERROR",
                    "Failed to parse flow definition.");
        }
    }

    private JsonNode findNodeByType(JsonNode definition, String type) {
        JsonNode nodes = definition.path("nodes");
        if (nodes.isArray()) {
            for (JsonNode n : nodes) {
                if (type.equals(n.path("type").asText())) return n;
            }
        }
        return null;
    }

    private JsonNode findNodeById(JsonNode definition, String nodeId) {
        JsonNode nodes = definition.path("nodes");
        if (nodes.isArray()) {
            for (JsonNode n : nodes) {
                if (nodeId.equals(n.path("id").asText())) return n;
            }
        }
        return null;
    }

    private String findNextNodeId(JsonNode definition, String sourceNodeId, String sourceHandle) {
        JsonNode edges = definition.path("edges");
        if (edges.isArray()) {
            for (JsonNode edge : edges) {
                if (sourceNodeId.equals(edge.path("source").asText())) {
                    if (sourceHandle != null) {
                        if (sourceHandle.equals(edge.path("sourceHandle").asText(null))) {
                            return edge.path("target").asText(null);
                        }
                    } else {
                        return edge.path("target").asText(null);
                    }
                }
            }
        }
        return null;
    }

    private boolean matchesKeyword(String text, String triggerKeywords) {
        if (triggerKeywords == null || triggerKeywords.isBlank()) return false;
        String normalizedText = text.trim().toLowerCase();
        for (String keyword : triggerKeywords.split(",")) {
            if (normalizedText.equals(keyword.trim().toLowerCase())) return true;
        }
        return false;
    }

    private String extractTypeFromResponse(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) return "null";
        try {
            return objectMapper.readTree(responseJson).path("type").asText("unknown");
        } catch (Exception e) {
            return "parse_error";
        }
    }

    private String extractTextFromResponse(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            return switch (root.path("type").asText("")) {
                case "text" -> root.path("text").asText(null);
                case "button" -> root.path("text").asText(null);
                case "interactive" -> root.path("title").asText(null);
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private String extractIdFromResponse(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String id = root.path("id").asText(null);
            if (id != null) return id;
            if ("button".equals(root.path("type").asText(""))) {
                return root.path("payload").asText(null);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void recordStep(UUID executionId, String nodeId, String nodeType,
                            FlowStepHistory.StepAction action, UUID messageId,
                            String responseData, String matchedCondition) {
        FlowStepHistory step = new FlowStepHistory();
        step.setExecutionId(executionId);
        step.setNodeId(nodeId);
        step.setNodeType(nodeType);
        step.setAction(action);
        step.setMessageId(messageId);
        step.setResponseData(responseData);
        step.setMatchedCondition(matchedCondition);
        flowStepHistoryRepository.save(step);
    }
}
