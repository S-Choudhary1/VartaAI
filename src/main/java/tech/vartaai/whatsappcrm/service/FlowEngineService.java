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

    @Transactional
    public void processIncomingMessage(Contact contact, Client client, String responseJson, String providerMessageId) {
        try {
            UUID contactId = contact.getId();
            UUID clientId = client.getId();

            // 1. Check if contact has active/waiting flow execution
            List<FlowExecution> activeExecs = flowExecutionRepository
                    .findActiveExecutionsForContact(contactId, clientId);

            if (!activeExecs.isEmpty()) {
                FlowExecution exec = activeExecs.get(0);
                if (exec.getStatus() == FlowExecution.ExecutionStatus.WAITING) {
                    handleWaitingExecution(exec, contact, client, responseJson);
                    return;
                }
            }

            // 2. No active execution — check for keyword-triggered flows
            String incomingText = extractTextFromResponse(responseJson);
            if (incomingText == null || incomingText.isBlank()) return;

            List<Flow> activeFlows = flowRepository.findByClient_IdAndStatusAndTriggerType(
                    clientId, Flow.FlowStatus.ACTIVE, Flow.TriggerType.KEYWORD);

            for (Flow flow : activeFlows) {
                if (matchesKeyword(incomingText, flow.getTriggerKeywords())) {
                    log.info("FLOW_KEYWORD_MATCH flowId={} contactId={} keyword={}",
                            flow.getId(), contactId, incomingText);
                    startFlowForContact(flow, contact, null);
                    return;
                }
            }
        } catch (Exception e) {
            log.error("FLOW_ENGINE_ERROR contactId={} err={}", contact.getId(), e.getMessage(), e);
        }
    }

    @Transactional
    public FlowExecution startFlowForContact(Flow flow, Contact contact, UUID campaignId) {
        // Re-fetch flow to ensure it's managed in this transaction (avoids LazyInitializationException)
        flow = flowRepository.findById(flow.getId()).orElse(flow);
        UUID clientId = flow.getClient().getId();

        // Check if contact already has an active execution for this flow
        List<FlowExecution> existing = flowExecutionRepository
                .findActiveExecutionsForContact(contact.getId(), clientId);
        for (FlowExecution ex : existing) {
            if (ex.getFlow().getId().equals(flow.getId())) {
                log.info("FLOW_ALREADY_ACTIVE flowId={} contactId={}", flow.getId(), contact.getId());
                return ex;
            }
        }

        FlowExecution exec = new FlowExecution();
        exec.setFlow(flow);
        exec.setClientId(clientId);
        exec.setContactId(contact.getId());
        exec.setCampaignId(campaignId);
        exec.setStatus(FlowExecution.ExecutionStatus.ACTIVE);
        flowExecutionRepository.save(exec);

        log.info("FLOW_STARTED flowId={} execId={} contactId={}", flow.getId(), exec.getId(), contact.getId());

        // Parse definition and find START node
        JsonNode definition = parseDefinition(flow.getDefinitionJson());
        JsonNode startNode = findNodeByType(definition, "START");

        if (startNode == null) {
            log.error("FLOW_NO_START_NODE flowId={}", flow.getId());
            exec.setStatus(FlowExecution.ExecutionStatus.FAILED);
            flowExecutionRepository.save(exec);
            return exec;
        }

        String startNodeId = startNode.path("id").asText();
        exec.setCurrentNodeId(startNodeId);
        flowExecutionRepository.save(exec);

        recordStep(exec.getId(), startNodeId, "START", FlowStepHistory.StepAction.ENTERED, null, null, null);

        // Advance from START to next node
        String nextNodeId = findNextNodeId(definition, startNodeId, null);
        if (nextNodeId != null) {
            executeNode(exec, definition, nextNodeId, contact, flow.getClient());
        }

        return exec;
    }

    @Transactional
    public void handleTimedOutExecution(FlowExecution exec) {
        // Re-fetch to ensure entity is managed in this transaction
        exec = flowExecutionRepository.findById(exec.getId()).orElse(exec);
        exec.setStatus(FlowExecution.ExecutionStatus.TIMED_OUT);
        exec.setCompletedAt(OffsetDateTime.now());
        flowExecutionRepository.save(exec);
        recordStep(exec.getId(), exec.getCurrentNodeId(), "WAIT_FOR_REPLY",
                FlowStepHistory.StepAction.FAILED, null, null, null);
        log.info("FLOW_TIMED_OUT execId={}", exec.getId());
    }

    // ═══════════════════════════════════════════════════════════════
    //  INTERNAL ENGINE LOGIC
    // ═══════════════════════════════════════════════════════════════

    private void handleWaitingExecution(FlowExecution exec, Contact contact, Client client, String responseJson) {
        Flow flow = exec.getFlow();
        JsonNode definition = parseDefinition(flow.getDefinitionJson());
        String waitNodeId = exec.getCurrentNodeId();

        recordStep(exec.getId(), waitNodeId, "WAIT_FOR_REPLY",
                FlowStepHistory.StepAction.RESPONSE_RECEIVED, null, responseJson, null);

        exec.setStatus(FlowExecution.ExecutionStatus.ACTIVE);
        flowExecutionRepository.save(exec);

        // Find the next node after WAIT_FOR_REPLY
        String nextNodeId = findNextNodeId(definition, waitNodeId, null);
        if (nextNodeId == null) {
            completeExecution(exec);
            return;
        }

        JsonNode nextNode = findNodeById(definition, nextNodeId);
        if (nextNode == null) {
            completeExecution(exec);
            return;
        }

        String nextType = nextNode.path("type").asText();

        if ("CONDITION".equals(nextType)) {
            evaluateCondition(exec, definition, nextNode, responseJson, contact, client);
        } else {
            executeNode(exec, definition, nextNodeId, contact, client);
        }
    }

    private void executeNode(FlowExecution exec, JsonNode definition, String nodeId, Contact contact, Client client) {
        JsonNode node = findNodeById(definition, nodeId);
        if (node == null) {
            log.warn("FLOW_NODE_NOT_FOUND execId={} nodeId={}", exec.getId(), nodeId);
            completeExecution(exec);
            return;
        }

        String type = node.path("type").asText();
        exec.setCurrentNodeId(nodeId);
        flowExecutionRepository.save(exec);

        recordStep(exec.getId(), nodeId, type, FlowStepHistory.StepAction.ENTERED, null, null, null);

        switch (type) {
            case "SEND_MESSAGE" -> executeSendMessage(exec, definition, node, contact, client);
            case "WAIT_FOR_REPLY" -> executeWaitForReply(exec, node);
            case "CONDITION" -> {
                // Condition without preceding WAIT — use default
                evaluateCondition(exec, definition, node, null, contact, client);
            }
            case "END" -> completeExecution(exec);
            default -> {
                log.warn("FLOW_UNKNOWN_NODE_TYPE type={} nodeId={}", type, nodeId);
                String nextNodeId = findNextNodeId(definition, nodeId, null);
                if (nextNodeId != null) {
                    executeNode(exec, definition, nextNodeId, contact, client);
                } else {
                    completeExecution(exec);
                }
            }
        }
    }

    private void executeSendMessage(FlowExecution exec, JsonNode definition, JsonNode node,
                                    Contact contact, Client client) {
        String nodeId = node.path("id").asText();
        JsonNode data = node.path("data");
        String messageType = data.path("messageType").asText("TEXT");

        try {
            SendMessageRequest req = new SendMessageRequest();
            req.setTo(contact.getPhone());

            switch (messageType) {
                case "TEXT" -> {
                    req.setMessageType(Message.MessageType.TEXT);
                    String body = data.path("text").path("body").asText("");
                    req.setText(new TextPayload(body, null));
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
                    }
                }
                case "INTERACTIVE" -> {
                    req.setMessageType(Message.MessageType.INTERACTIVE);
                    JsonNode interactiveNode = data.path("interactive");
                    if (interactiveNode.isObject()) {
                        InteractivePayload payload = objectMapper.treeToValue(interactiveNode, InteractivePayload.class);
                        req.setInteractive(payload);
                    }
                }
                default -> {
                    req.setMessageType(Message.MessageType.TEXT);
                    req.setText(new TextPayload(data.path("text").path("body").asText(""), null));
                }
            }

            SendResponse resp = messageService.sendMessage(req, client.getId());

            UUID messageId = null;
            if (resp != null && resp.getId() != null) {
                messageId = UUID.fromString(resp.getId());
                // Link message to flow execution
                Message msg = messageRepository.findById(messageId).orElse(null);
                if (msg != null) {
                    msg.setFlowExecutionId(exec.getId());
                    messageRepository.save(msg);
                }
            }

            recordStep(exec.getId(), nodeId, "SEND_MESSAGE",
                    FlowStepHistory.StepAction.MESSAGE_SENT, messageId, null, null);

            // Advance to next node
            String nextNodeId = findNextNodeId(definition, nodeId, null);
            if (nextNodeId != null) {
                executeNode(exec, definition, nextNodeId, contact, client);
            } else {
                completeExecution(exec);
            }

        } catch (Exception e) {
            log.error("FLOW_SEND_ERROR execId={} nodeId={} err={}", exec.getId(), nodeId, e.getMessage());
            recordStep(exec.getId(), nodeId, "SEND_MESSAGE",
                    FlowStepHistory.StepAction.FAILED, null, null, null);
            // Continue despite send failure — don't kill the entire flow
            String nextNodeId = findNextNodeId(definition, nodeId, null);
            if (nextNodeId != null) {
                executeNode(exec, definition, nextNodeId, contact, client);
            } else {
                completeExecution(exec);
            }
        }
    }

    private void executeWaitForReply(FlowExecution exec, JsonNode node) {
        exec.setStatus(FlowExecution.ExecutionStatus.WAITING);

        int timeoutSeconds = node.path("data").path("timeoutSeconds").asInt(0);
        if (timeoutSeconds > 0) {
            exec.setResumeAfter(OffsetDateTime.now().plusSeconds(timeoutSeconds));
        }

        flowExecutionRepository.save(exec);
        log.info("FLOW_WAITING execId={} nodeId={} timeout={}s",
                exec.getId(), node.path("id").asText(), timeoutSeconds);
    }

    private void evaluateCondition(FlowExecution exec, JsonNode definition, JsonNode condNode,
                                   String responseJson, Contact contact, Client client) {
        String nodeId = condNode.path("id").asText();

        // Ensure currentNodeId tracks the CONDITION node for analytics
        exec.setCurrentNodeId(nodeId);
        flowExecutionRepository.save(exec);

        JsonNode conditions = condNode.path("data").path("conditions");

        String responseText = extractTextFromResponse(responseJson);
        String responseId = extractIdFromResponse(responseJson);

        String matchedConditionId = null;
        String defaultConditionId = null;

        if (conditions.isArray()) {
            for (JsonNode cond : conditions) {
                String condId = cond.path("id").asText();
                String matchType = cond.path("matchType").asText("");
                String value = cond.path("value").asText("");

                if ("DEFAULT".equals(matchType)) {
                    defaultConditionId = condId;
                    continue;
                }

                boolean matched = switch (matchType) {
                    case "EXACT" -> responseText != null && responseText.equalsIgnoreCase(value);
                    case "CONTAINS" -> responseText != null && responseText.toLowerCase().contains(value.toLowerCase());
                    case "BUTTON_ID" -> value.equals(responseId);
                    case "LIST_ID" -> value.equals(responseId);
                    case "REGEX" -> {
                        try {
                            yield responseText != null && Pattern.compile(value, Pattern.CASE_INSENSITIVE)
                                    .matcher(responseText).find();
                        } catch (Exception e) {
                            yield false;
                        }
                    }
                    default -> false;
                };

                if (matched) {
                    matchedConditionId = condId;
                    break;
                }
            }
        }

        if (matchedConditionId == null) {
            matchedConditionId = defaultConditionId;
        }

        recordStep(exec.getId(), nodeId, "CONDITION",
                FlowStepHistory.StepAction.CONDITION_EVALUATED, null, responseJson, matchedConditionId);

        if (matchedConditionId != null) {
            String nextNodeId = findNextNodeId(definition, nodeId, matchedConditionId);
            if (nextNodeId != null) {
                executeNode(exec, definition, nextNodeId, contact, client);
            } else {
                completeExecution(exec);
            }
        } else {
            log.info("FLOW_NO_CONDITION_MATCH execId={} nodeId={}", exec.getId(), nodeId);
            completeExecution(exec);
        }
    }

    private void completeExecution(FlowExecution exec) {
        exec.setStatus(FlowExecution.ExecutionStatus.COMPLETED);
        exec.setCompletedAt(OffsetDateTime.now());
        flowExecutionRepository.save(exec);

        recordStep(exec.getId(), exec.getCurrentNodeId(), "END",
                FlowStepHistory.StepAction.COMPLETED, null, null, null);

        log.info("FLOW_COMPLETED execId={}", exec.getId());
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    private JsonNode parseDefinition(String definitionJson) {
        try {
            return objectMapper.readTree(definitionJson);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FLOW_PARSE_ERROR",
                    "Failed to parse flow definition.");
        }
    }

    private JsonNode findNodeByType(JsonNode definition, String type) {
        JsonNode nodes = definition.path("nodes");
        if (nodes.isArray()) {
            for (JsonNode n : nodes) {
                if (type.equals(n.path("type").asText())) {
                    return n;
                }
            }
        }
        return null;
    }

    private JsonNode findNodeById(JsonNode definition, String nodeId) {
        JsonNode nodes = definition.path("nodes");
        if (nodes.isArray()) {
            for (JsonNode n : nodes) {
                if (nodeId.equals(n.path("id").asText())) {
                    return n;
                }
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
                        String edgeHandle = edge.path("sourceHandle").asText(null);
                        if (sourceHandle.equals(edgeHandle)) {
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
        String[] keywords = triggerKeywords.split(",");
        for (String keyword : keywords) {
            if (normalizedText.equals(keyword.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String extractTextFromResponse(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String type = root.path("type").asText("");
            return switch (type) {
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
            return root.path("id").asText(null);
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
