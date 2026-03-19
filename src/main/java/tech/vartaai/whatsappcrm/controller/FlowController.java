package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.dto.FlowExecutionResponse;
import tech.vartaai.whatsappcrm.dto.FlowRequest;
import tech.vartaai.whatsappcrm.dto.FlowResponse;
import tech.vartaai.whatsappcrm.entity.Contact;
import tech.vartaai.whatsappcrm.entity.Flow;
import tech.vartaai.whatsappcrm.exception.ApiException;
import tech.vartaai.whatsappcrm.repository.ContactRepository;
import tech.vartaai.whatsappcrm.repository.FlowRepository;
import tech.vartaai.whatsappcrm.service.FlowEngineService;
import tech.vartaai.whatsappcrm.service.FlowService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flows")
public class FlowController {

    private final FlowService flowService;
    private final FlowEngineService flowEngineService;
    private final FlowRepository flowRepository;
    private final ContactRepository contactRepository;

    public FlowController(FlowService flowService,
                          FlowEngineService flowEngineService,
                          FlowRepository flowRepository,
                          ContactRepository contactRepository) {
        this.flowService = flowService;
        this.flowEngineService = flowEngineService;
        this.flowRepository = flowRepository;
        this.contactRepository = contactRepository;
    }

    @PostMapping
    public ResponseEntity<FlowResponse> createFlow(
            @Valid @RequestBody FlowRequest request,
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        FlowResponse response = flowService.createFlow(request, clientId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<FlowResponse>> listFlows(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(flowService.listFlows(clientId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlowResponse> getFlow(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(flowService.getFlow(id, clientId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FlowResponse> updateFlow(
            @PathVariable UUID id,
            @Valid @RequestBody FlowRequest request,
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(flowService.updateFlow(id, request, clientId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlow(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") UUID clientId) {
        flowService.deleteFlow(id, clientId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<FlowResponse> activateFlow(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(flowService.activateFlow(id, clientId));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<FlowResponse> pauseFlow(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(flowService.pauseFlow(id, clientId));
    }

    @GetMapping("/{id}/executions")
    public ResponseEntity<Page<FlowExecutionResponse>> listExecutions(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(flowService.listExecutions(id, clientId, pageable));
    }

    @GetMapping("/{id}/executions/{execId}")
    public ResponseEntity<FlowExecutionResponse> getExecution(
            @PathVariable UUID id,
            @PathVariable UUID execId,
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(flowService.getExecution(id, execId, clientId));
    }

    @GetMapping("/{id}/analytics")
    public ResponseEntity<Map<String, Object>> getFlowAnalytics(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(flowService.getFlowAnalytics(id, clientId));
    }

    @PostMapping("/{id}/enroll")
    public ResponseEntity<Map<String, String>> enrollContact(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestBody Map<String, String> body) {
        String contactIdStr = body.get("contactId");
        if (contactIdStr == null || contactIdStr.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "contactId is required.");
        }

        UUID contactId = UUID.fromString(contactIdStr);
        Flow flow = flowRepository.findByIdAndClient_Id(id, clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FLOW_NOT_FOUND", "Flow not found."));

        if (flow.getStatus() != Flow.FlowStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FLOW_NOT_ACTIVE", "Flow is not active.");
        }

        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTACT_NOT_FOUND", "Contact not found."));

        flowEngineService.startFlowForContact(flow, contact, null);
        return ResponseEntity.ok(Map.of("status", "enrolled"));
    }
}
