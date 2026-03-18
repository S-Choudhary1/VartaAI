package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.vartaai.whatsappcrm.dto.FlowCreateRequest;
import tech.vartaai.whatsappcrm.dto.FlowCreateWithVersionRequest;
import tech.vartaai.whatsappcrm.dto.FlowResponse;
import tech.vartaai.whatsappcrm.dto.FlowVersionCreateRequest;
import tech.vartaai.whatsappcrm.dto.FlowVersionResponse;
import tech.vartaai.whatsappcrm.service.FlowService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flows")
public class FlowController {

    private final FlowService flowService;

    public FlowController(FlowService flowService) {
        this.flowService = flowService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<FlowResponse> createFlow(@RequestHeader("X-Client-Id") UUID clientId,
                                                   @Valid @RequestBody FlowCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(flowService.createFlow(clientId, request));
    }

    @PostMapping("/with-version")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<FlowVersionResponse> createFlowWithVersion(@RequestHeader("X-Client-Id") UUID clientId,
                                                                     @Valid @RequestBody FlowCreateWithVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(flowService.createFlowWithVersion(clientId, request));
    }

    @GetMapping
    public ResponseEntity<List<FlowResponse>> listFlows(@RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(flowService.listFlows(clientId));
    }

    @PostMapping("/{flowId}/versions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<FlowVersionResponse> createVersion(@RequestHeader("X-Client-Id") UUID clientId,
                                                             @PathVariable UUID flowId,
                                                             @Valid @RequestBody FlowVersionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(flowService.createVersion(clientId, flowId, request));
    }

    @PostMapping("/versions/{versionId}/publish")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<FlowVersionResponse> publishVersion(@RequestHeader("X-Client-Id") UUID clientId,
                                                              @PathVariable UUID versionId) {
        return ResponseEntity.ok(flowService.publishVersion(clientId, versionId));
    }
}
