package tech.vartaai.whatsappcrm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.vartaai.whatsappcrm.dto.FlowExecutionResponse;
import tech.vartaai.whatsappcrm.dto.FlowRequest;
import tech.vartaai.whatsappcrm.dto.FlowResponse;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Flow;
import tech.vartaai.whatsappcrm.entity.FlowExecution;
import tech.vartaai.whatsappcrm.entity.FlowStepHistory;
import tech.vartaai.whatsappcrm.exception.ApiException;
import tech.vartaai.whatsappcrm.repository.FlowExecutionRepository;
import tech.vartaai.whatsappcrm.repository.FlowRepository;
import tech.vartaai.whatsappcrm.repository.FlowStepHistoryRepository;

import java.util.*;

@Service
@Slf4j
public class FlowService {

    private final FlowRepository flowRepository;
    private final FlowExecutionRepository flowExecutionRepository;
    private final FlowStepHistoryRepository flowStepHistoryRepository;

    public FlowService(FlowRepository flowRepository,
                       FlowExecutionRepository flowExecutionRepository,
                       FlowStepHistoryRepository flowStepHistoryRepository) {
        this.flowRepository = flowRepository;
        this.flowExecutionRepository = flowExecutionRepository;
        this.flowStepHistoryRepository = flowStepHistoryRepository;
    }

    @Transactional
    public FlowResponse createFlow(FlowRequest request, UUID clientId, UUID createdBy) {
        log.info("FLOW_CREATE clientId={} name='{}' triggerType={} createdBy={}",
                clientId, request.getName(), request.getTriggerType(), createdBy);

        Flow flow = new Flow();
        Client client = new Client();
        client.setId(clientId);
        flow.setClient(client);
        flow.setName(request.getName());
        flow.setDescription(request.getDescription());
        flow.setStatus(Flow.FlowStatus.DRAFT);

        if (request.getTriggerType() != null) {
            flow.setTriggerType(Flow.TriggerType.valueOf(request.getTriggerType()));
        }
        flow.setTriggerKeywords(request.getTriggerKeywords());
        flow.setDefinitionJson(request.getDefinitionJson());
        flow.setCreatedBy(createdBy);

        Flow saved = flowRepository.save(flow);
        log.info("FLOW_CREATED flowId={} name='{}' status={}", saved.getId(), saved.getName(), saved.getStatus());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public FlowResponse getFlow(UUID id, UUID clientId) {
        Flow flow = flowRepository.findByIdAndClient_Id(id, clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FLOW_NOT_FOUND", "Flow not found."));
        return toResponse(flow);
    }

    @Transactional(readOnly = true)
    public Page<FlowResponse> listFlows(UUID clientId, Pageable pageable) {
        return flowRepository.findByClient_IdOrderByCreatedAtDesc(clientId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public FlowResponse updateFlow(UUID id, FlowRequest request, UUID clientId) {
        log.info("FLOW_UPDATE flowId={} clientId={} name='{}'", id, clientId, request.getName());
        Flow flow = flowRepository.findByIdAndClient_Id(id, clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FLOW_NOT_FOUND", "Flow not found."));

        flow.setName(request.getName());
        flow.setDescription(request.getDescription());

        if (request.getTriggerType() != null) {
            flow.setTriggerType(Flow.TriggerType.valueOf(request.getTriggerType()));
        }
        flow.setTriggerKeywords(request.getTriggerKeywords());

        if (request.getDefinitionJson() != null) {
            flow.setDefinitionJson(request.getDefinitionJson());
            flow.setVersion(flow.getVersion() + 1);
        }

        Flow saved = flowRepository.save(flow);
        log.info("FLOW_UPDATED flowId={} version={} status={}", saved.getId(), saved.getVersion(), saved.getStatus());
        return toResponse(saved);
    }

    @Transactional
    public void deleteFlow(UUID id, UUID clientId) {
        log.info("FLOW_DELETE flowId={} clientId={}", id, clientId);
        Flow flow = flowRepository.findByIdAndClient_Id(id, clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FLOW_NOT_FOUND", "Flow not found."));
        flow.setStatus(Flow.FlowStatus.ARCHIVED);
        flowRepository.save(flow);
        log.info("FLOW_ARCHIVED flowId={} name='{}'", flow.getId(), flow.getName());
    }

    @Transactional
    public FlowResponse activateFlow(UUID id, UUID clientId) {
        log.info("FLOW_ACTIVATE flowId={} clientId={}", id, clientId);
        Flow flow = flowRepository.findByIdAndClient_Id(id, clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FLOW_NOT_FOUND", "Flow not found."));

        if (flow.getDefinitionJson() == null || flow.getDefinitionJson().isBlank()) {
            log.warn("FLOW_ACTIVATE_FAILED flowId={} — no definition", flow.getId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "FLOW_NO_DEFINITION",
                    "Cannot activate a flow without a definition.");
        }

        flow.setStatus(Flow.FlowStatus.ACTIVE);
        Flow saved = flowRepository.save(flow);
        log.info("FLOW_ACTIVATED flowId={} name='{}' triggerType={} keywords='{}'",
                saved.getId(), saved.getName(), saved.getTriggerType(), saved.getTriggerKeywords());
        return toResponse(saved);
    }

    @Transactional
    public FlowResponse pauseFlow(UUID id, UUID clientId) {
        log.info("FLOW_PAUSE flowId={} clientId={}", id, clientId);
        Flow flow = flowRepository.findByIdAndClient_Id(id, clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FLOW_NOT_FOUND", "Flow not found."));
        flow.setStatus(Flow.FlowStatus.PAUSED);
        Flow saved = flowRepository.save(flow);
        log.info("FLOW_PAUSED flowId={} name='{}'", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<FlowExecutionResponse> listExecutions(UUID flowId, UUID clientId, Pageable pageable) {
        return flowExecutionRepository.findByFlow_IdAndClientIdOrderByStartedAtDesc(flowId, clientId, pageable)
                .map(this::toExecutionResponse);
    }

    @Transactional(readOnly = true)
    public FlowExecutionResponse getExecution(UUID flowId, UUID execId, UUID clientId) {
        FlowExecution exec = flowExecutionRepository.findByIdAndClientId(execId, clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EXECUTION_NOT_FOUND",
                        "Flow execution not found."));

        if (!exec.getFlow().getId().equals(flowId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EXECUTION_NOT_FOUND",
                    "Flow execution not found.");
        }

        FlowExecutionResponse resp = toExecutionResponse(exec);
        List<FlowStepHistory> steps = flowStepHistoryRepository
                .findByExecutionIdOrderByCreatedAtAsc(execId);
        resp.setSteps(steps.stream().map(this::toStepItem).toList());
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFlowAnalytics(UUID flowId, UUID clientId) {
        // Verify flow belongs to client
        flowRepository.findByIdAndClient_Id(flowId, clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FLOW_NOT_FOUND", "Flow not found."));

        List<Object[]> rows = flowExecutionRepository.countGroupedByStatus(flowId, clientId);

        long completed = 0, active = 0, waiting = 0, failed = 0, timedOut = 0, total = 0;
        for (Object[] row : rows) {
            FlowExecution.ExecutionStatus status = (FlowExecution.ExecutionStatus) row[0];
            long count = ((Number) row[1]).longValue();
            total += count;
            switch (status) {
                case COMPLETED -> completed = count;
                case ACTIVE    -> active = count;
                case WAITING   -> waiting = count;
                case FAILED    -> failed = count;
                case TIMED_OUT -> timedOut = count;
            }
        }

        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("totalExecutions", total);
        analytics.put("completed", completed);
        analytics.put("active", active);
        analytics.put("waiting", waiting);
        analytics.put("failed", failed);
        analytics.put("timedOut", timedOut);
        return analytics;
    }

    private FlowResponse toResponse(Flow flow) {
        return FlowResponse.builder()
                .id(flow.getId())
                .name(flow.getName())
                .description(flow.getDescription())
                .status(flow.getStatus().name())
                .triggerType(flow.getTriggerType() != null ? flow.getTriggerType().name() : null)
                .triggerKeywords(flow.getTriggerKeywords())
                .definitionJson(flow.getDefinitionJson())
                .version(flow.getVersion())
                .createdBy(flow.getCreatedBy())
                .createdAt(flow.getCreatedAt())
                .updatedAt(flow.getUpdatedAt())
                .build();
    }

    private FlowExecutionResponse toExecutionResponse(FlowExecution exec) {
        return FlowExecutionResponse.builder()
                .id(exec.getId())
                .flowId(exec.getFlow().getId())
                .contactId(exec.getContactId())
                .campaignId(exec.getCampaignId())
                .currentNodeId(exec.getCurrentNodeId())
                .status(exec.getStatus().name())
                .startedAt(exec.getStartedAt())
                .updatedAt(exec.getUpdatedAt())
                .completedAt(exec.getCompletedAt())
                .build();
    }

    private FlowExecutionResponse.StepHistoryItem toStepItem(FlowStepHistory step) {
        return FlowExecutionResponse.StepHistoryItem.builder()
                .id(step.getId())
                .nodeId(step.getNodeId())
                .nodeType(step.getNodeType())
                .action(step.getAction().name())
                .messageId(step.getMessageId())
                .responseData(step.getResponseData())
                .matchedCondition(step.getMatchedCondition())
                .createdAt(step.getCreatedAt())
                .build();
    }
}
