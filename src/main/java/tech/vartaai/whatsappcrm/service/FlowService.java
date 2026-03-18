package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.vartaai.whatsappcrm.dto.FlowCreateRequest;
import tech.vartaai.whatsappcrm.dto.FlowCreateWithVersionRequest;
import tech.vartaai.whatsappcrm.dto.FlowResponse;
import tech.vartaai.whatsappcrm.dto.FlowRunReportItem;
import tech.vartaai.whatsappcrm.dto.FlowStepRequest;
import tech.vartaai.whatsappcrm.dto.FlowTransitionRequest;
import tech.vartaai.whatsappcrm.dto.FlowVersionCreateRequest;
import tech.vartaai.whatsappcrm.dto.FlowVersionResponse;
import tech.vartaai.whatsappcrm.dto.SendMessageRequest;
import tech.vartaai.whatsappcrm.entity.Campaign;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.ContactFlowRun;
import tech.vartaai.whatsappcrm.entity.FlowDefinition;
import tech.vartaai.whatsappcrm.entity.FlowRunEvent;
import tech.vartaai.whatsappcrm.entity.FlowStep;
import tech.vartaai.whatsappcrm.entity.FlowTransition;
import tech.vartaai.whatsappcrm.entity.FlowVersion;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.repository.ContactFlowRunRepository;
import tech.vartaai.whatsappcrm.repository.FlowDefinitionRepository;
import tech.vartaai.whatsappcrm.repository.FlowRunEventRepository;
import tech.vartaai.whatsappcrm.repository.FlowStepRepository;
import tech.vartaai.whatsappcrm.repository.FlowTransitionRepository;
import tech.vartaai.whatsappcrm.repository.FlowVersionRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FlowService {
    private final FlowDefinitionRepository flowDefinitionRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final FlowStepRepository flowStepRepository;
    private final FlowTransitionRepository flowTransitionRepository;
    private final ContactFlowRunRepository contactFlowRunRepository;
    private final FlowRunEventRepository flowRunEventRepository;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    public FlowService(FlowDefinitionRepository flowDefinitionRepository,
                       FlowVersionRepository flowVersionRepository,
                       FlowStepRepository flowStepRepository,
                       FlowTransitionRepository flowTransitionRepository,
                       ContactFlowRunRepository contactFlowRunRepository,
                       FlowRunEventRepository flowRunEventRepository,
                       MessageService messageService,
                       ObjectMapper objectMapper) {
        this.flowDefinitionRepository = flowDefinitionRepository;
        this.flowVersionRepository = flowVersionRepository;
        this.flowStepRepository = flowStepRepository;
        this.flowTransitionRepository = flowTransitionRepository;
        this.contactFlowRunRepository = contactFlowRunRepository;
        this.flowRunEventRepository = flowRunEventRepository;
        this.messageService = messageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FlowResponse createFlow(UUID clientId, FlowCreateRequest request) {
        FlowDefinition flow = new FlowDefinition();
        Client client = new Client();
        client.setId(clientId);
        flow.setClient(client);
        flow.setName(request.getName());
        flow.setDescription(request.getDescription());
        FlowDefinition saved = flowDefinitionRepository.save(flow);
        return toResponse(saved);
    }

    @Transactional
    public FlowVersionResponse createFlowWithVersion(UUID clientId, FlowCreateWithVersionRequest request) {
        FlowDefinition flow = new FlowDefinition();
        Client client = new Client();
        client.setId(clientId);
        flow.setClient(client);
        flow.setName(request.getName());
        flow.setDescription(request.getDescription());
        FlowDefinition saved = flowDefinitionRepository.save(flow);
        return persistVersion(saved, request.getVersion());
    }

    public List<FlowResponse> listFlows(UUID clientId) {
        return flowDefinitionRepository.findByClient_IdOrderByCreatedAtDesc(clientId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public FlowVersionResponse createVersion(UUID clientId, UUID flowId, FlowVersionCreateRequest request) {
        FlowDefinition flow = flowDefinitionRepository.findById(flowId)
                .orElseThrow(() -> new RuntimeException("Flow not found"));
        if (!flow.getClient().getId().equals(clientId)) throw new RuntimeException("Flow not found");
        return persistVersion(flow, request);
    }

    @Transactional
    public FlowVersionResponse publishVersion(UUID clientId, UUID versionId) {
        FlowVersion version = flowVersionRepository.findById(versionId)
                .orElseThrow(() -> new RuntimeException("Flow version not found"));
        if (!version.getFlowDefinition().getClient().getId().equals(clientId)) {
            throw new RuntimeException("Flow version not found");
        }
        version.setStatus(FlowVersion.VersionStatus.PUBLISHED);
        version.setPublishedAt(java.time.OffsetDateTime.now());
        return toVersionResponse(flowVersionRepository.save(version));
    }

    @Transactional
    public void startFlowForCampaignContact(Campaign campaign, UUID flowVersionId, String phone, Map<String, String> variables, UUID clientId) {
        FlowVersion version = flowVersionRepository.findById(flowVersionId)
                .orElseThrow(() -> new RuntimeException("Flow version not found"));
        if (!version.getFlowDefinition().getClient().getId().equals(clientId)) {
            throw new RuntimeException("Flow version not found");
        }
        if (version.getStatus() != FlowVersion.VersionStatus.PUBLISHED) {
            throw new RuntimeException("Flow version must be published");
        }

        Optional<ContactFlowRun> active = contactFlowRunRepository.findByClient_IdAndContactIdAndStatus(
                clientId, phone, ContactFlowRun.RunStatus.ACTIVE
        );
        if (active.isPresent()) {
            return;
        }

        ContactFlowRun run = new ContactFlowRun();
        Client client = new Client();
        client.setId(clientId);
        run.setClient(client);
        run.setCampaign(campaign);
        run.setFlowVersion(version);
        run.setContactId(phone);
        run.setCurrentStepKey(version.getStartStepKey());
        run.setStatus(ContactFlowRun.RunStatus.ACTIVE);
        ContactFlowRun savedRun = contactFlowRunRepository.save(run);

        addEvent(savedRun, FlowRunEvent.EventType.STARTED, version.getStartStepKey(), Map.of("phone", phone));
        sendStep(savedRun, version.getStartStepKey(), variables, clientId);
    }

    @Transactional
    public void handleInboundResponse(UUID clientId, String phone, String responseJson, String contextProviderMessageId) {
        Optional<ContactFlowRun> runOpt = contactFlowRunRepository
                .findByClient_IdAndContactIdAndLastOutboundProviderMessageIdAndStatus(
                        clientId, phone, contextProviderMessageId, ContactFlowRun.RunStatus.ACTIVE
                );
        if (runOpt.isEmpty()) {
            return;
        }
        ContactFlowRun run = runOpt.get();
        run.setLastResponse(extractResponseKey(responseJson));
        contactFlowRunRepository.save(run);
        addEvent(run, FlowRunEvent.EventType.RESPONSE_RECEIVED, run.getCurrentStepKey(),
                Map.of("response", run.getLastResponse(), "raw", responseJson));

        List<FlowTransition> transitions = flowTransitionRepository
                .findByFlowVersion_IdAndFromStepKey(run.getFlowVersion().getId(), run.getCurrentStepKey());
        FlowTransition matched = matchTransition(transitions, run.getLastResponse());
        if (matched == null) {
            return;
        }
        if (matched.getNextStepKey() == null || matched.getNextStepKey().isBlank()) {
            run.setStatus(ContactFlowRun.RunStatus.COMPLETED);
            contactFlowRunRepository.save(run);
            addEvent(run, FlowRunEvent.EventType.COMPLETED, run.getCurrentStepKey(), Map.of("reason", "terminal_transition"));
            return;
        }

        run.setCurrentStepKey(matched.getNextStepKey());
        contactFlowRunRepository.save(run);
        sendStep(run, matched.getNextStepKey(), Map.of(), clientId);
    }

    public List<FlowRunReportItem> getCampaignFlowReport(UUID campaignId, UUID clientId) {
        return contactFlowRunRepository.findByCampaign_IdAndClient_Id(campaignId, clientId).stream()
                .map(run -> FlowRunReportItem.builder()
                        .runId(run.getId())
                        .contactId(run.getContactId())
                        .phone(run.getContactId())
                        .currentStep(run.getCurrentStepKey())
                        .lastResponse(run.getLastResponse())
                        .status(run.getStatus().name())
                        .completed(run.getStatus() == ContactFlowRun.RunStatus.COMPLETED)
                        .failed(run.getStatus() == ContactFlowRun.RunStatus.FAILED)
                        .startedAt(run.getStartedAt())
                        .updatedAt(run.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private void sendStep(ContactFlowRun run, String stepKey, Map<String, String> variables, UUID clientId) {
        FlowStep step = flowStepRepository.findByFlowVersion_IdAndStepKey(run.getFlowVersion().getId(), stepKey)
                .orElseThrow(() -> new RuntimeException("Flow step not found: " + stepKey));

        try {
            SendMessageRequest req;
            if (step.getMessageType() == FlowStep.StepMessageType.TEMPLATE) {
                req = new SendMessageRequest(
                        run.getContactId(),
                        Message.MessageType.TEMPLATE,
                        null,
                        null,
                        step.getTemplateId(),
                        variables == null ? Map.of() : variables,
                        "META",
                        run.getCampaign() != null ? run.getCampaign().getId().toString() : null
                );
            } else if (step.getMessageType() == FlowStep.StepMessageType.TEXT) {
                req = new SendMessageRequest(
                        run.getContactId(),
                        Message.MessageType.TEXT,
                        step.getTextBody(),
                        null,
                        null,
                        Map.of(),
                        "META",
                        run.getCampaign() != null ? run.getCampaign().getId().toString() : null
                );
            } else {
                throw new RuntimeException("Only TEMPLATE and TEXT step types are supported in v1");
            }

            var resp = messageService.sendMessage(req, clientId);
            run.setLastOutboundProviderMessageId(resp.getProviderMessageId());
            contactFlowRunRepository.save(run);
            addEvent(run, FlowRunEvent.EventType.STEP_SENT, stepKey, Map.of("providerMessageId", resp.getProviderMessageId()));
        } catch (Exception ex) {
            run.setStatus(ContactFlowRun.RunStatus.FAILED);
            contactFlowRunRepository.save(run);
            addEvent(run, FlowRunEvent.EventType.FAILED, stepKey, Map.of("error", ex.getMessage()));
        }
    }

    private FlowTransition matchTransition(List<FlowTransition> transitions, String response) {
        if (transitions == null || transitions.isEmpty()) return null;
        String normalized = normalize(response);
        List<FlowTransition> ordered = new ArrayList<>(transitions);
        ordered.sort(Comparator.comparingInt(this::priority));
        for (FlowTransition transition : ordered) {
            switch (transition.getMatchOperator()) {
                case EQUALS:
                    if (normalize(transition.getMatchValue()).equals(normalized)) return transition;
                    break;
                case CONTAINS:
                    if (!normalize(transition.getMatchValue()).isBlank() && normalized.contains(normalize(transition.getMatchValue()))) {
                        return transition;
                    }
                    break;
                case DEFAULT:
                    return transition;
                default:
                    break;
            }
        }
        return null;
    }

    private int priority(FlowTransition t) {
        return switch (t.getMatchOperator()) {
            case EQUALS -> 1;
            case CONTAINS -> 2;
            case DEFAULT -> 3;
        };
    }

    private String extractResponseKey(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) return "";
        try {
            Map<String, Object> map = objectMapper.readValue(responseJson, new TypeReference<>() {});
            Object type = map.get("type");
            if ("button".equals(type)) {
                Object payload = map.get("payload");
                Object text = map.get("text");
                return payload != null ? payload.toString() : (text != null ? text.toString() : "");
            }
            if ("interactive".equals(type)) {
                Object id = map.get("id");
                Object title = map.get("title");
                return id != null ? id.toString() : (title != null ? title.toString() : "");
            }
            if ("text".equals(type)) {
                Object text = map.get("text");
                return text != null ? text.toString() : "";
            }
            return responseJson;
        } catch (Exception e) {
            return responseJson;
        }
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.trim().toUpperCase();
    }

    private void addEvent(ContactFlowRun run, FlowRunEvent.EventType type, String stepKey, Map<String, Object> payload) {
        FlowRunEvent event = new FlowRunEvent();
        event.setContactFlowRun(run);
        event.setEventType(type);
        event.setStepKey(stepKey);
        try {
            event.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            event.setPayloadJson("{}");
        }
        flowRunEventRepository.save(event);
    }

    private FlowResponse toResponse(FlowDefinition f) {
        return FlowResponse.builder()
                .id(f.getId())
                .name(f.getName())
                .description(f.getDescription())
                .active(f.isActive())
                .createdAt(f.getCreatedAt())
                .build();
    }

    private FlowVersionResponse toVersionResponse(FlowVersion v) {
        return FlowVersionResponse.builder()
                .id(v.getId())
                .flowId(v.getFlowDefinition().getId())
                .versionNumber(v.getVersionNumber())
                .status(v.getStatus().name())
                .startStepKey(v.getStartStepKey())
                .createdAt(v.getCreatedAt())
                .publishedAt(v.getPublishedAt())
                .build();
    }

    private FlowVersionResponse persistVersion(FlowDefinition flow, FlowVersionCreateRequest request) {
        int nextVersion = flowVersionRepository.findByFlowDefinition_IdOrderByVersionNumberDesc(flow.getId())
                .stream().findFirst().map(v -> v.getVersionNumber() + 1).orElse(1);

        FlowVersion version = new FlowVersion();
        version.setFlowDefinition(flow);
        version.setVersionNumber(nextVersion);
        version.setStartStepKey(request.getStartStepKey());
        if (request.isPublish()) {
            version.setStatus(FlowVersion.VersionStatus.PUBLISHED);
            version.setPublishedAt(java.time.OffsetDateTime.now());
        }
        FlowVersion savedVersion = flowVersionRepository.save(version);

        for (FlowStepRequest step : request.getSteps()) {
            FlowStep.StepMessageType stepType = FlowStep.StepMessageType.valueOf(step.getMessageType().toUpperCase());
            if (stepType != FlowStep.StepMessageType.TEMPLATE && stepType != FlowStep.StepMessageType.TEXT) {
                throw new RuntimeException("Only TEMPLATE and TEXT step types are supported in v1");
            }
            FlowStep fs = new FlowStep();
            fs.setFlowVersion(savedVersion);
            fs.setStepKey(step.getStepKey());
            fs.setMessageType(stepType);
            fs.setTemplateId(step.getTemplateId());
            fs.setTextBody(step.getTextBody());
            fs.setPayloadJson(step.getPayloadJson());
            flowStepRepository.save(fs);
        }
        for (FlowTransitionRequest t : request.getTransitions()) {
            FlowTransition ft = new FlowTransition();
            ft.setFlowVersion(savedVersion);
            ft.setFromStepKey(t.getFromStepKey());
            ft.setMatchOperator(FlowTransition.MatchOperator.valueOf(t.getOperator().toUpperCase()));
            ft.setMatchValue(t.getMatchValue());
            ft.setNextStepKey(t.getNextStepKey());
            flowTransitionRepository.save(ft);
        }
        return toVersionResponse(savedVersion);
    }
}
