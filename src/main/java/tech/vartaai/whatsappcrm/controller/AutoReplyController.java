package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.dto.AutoReplyFeatureFlagRequest;
import tech.vartaai.whatsappcrm.dto.AutoReplyFeatureFlagResponse;
import tech.vartaai.whatsappcrm.dto.AutoReplyRuleRequest;
import tech.vartaai.whatsappcrm.dto.AutoReplyRuleResponse;
import tech.vartaai.whatsappcrm.dto.AutoReplyRuleStatusRequest;
import tech.vartaai.whatsappcrm.service.AutoReplyService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auto-replies")
@CrossOrigin()
public class AutoReplyController {

    private final AutoReplyService autoReplyService;

    public AutoReplyController(AutoReplyService autoReplyService) {
        this.autoReplyService = autoReplyService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<AutoReplyRuleResponse> create(@Valid @RequestBody AutoReplyRuleRequest request,
                                                        @RequestHeader("X-Client-Id") UUID clientId) {
        AutoReplyRuleResponse response = autoReplyService.createRule(request, clientId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AutoReplyRuleResponse>> list(@RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(autoReplyService.getAllRules(clientId));
    }

    @GetMapping("/feature-flag")
    public ResponseEntity<AutoReplyFeatureFlagResponse> getFeatureFlag(@RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(autoReplyService.getFeatureFlag(clientId));
    }

    @PatchMapping("/feature-flag")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<AutoReplyFeatureFlagResponse> updateFeatureFlag(
            @Valid @RequestBody AutoReplyFeatureFlagRequest request,
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(autoReplyService.updateFeatureFlag(clientId, request.getEnabled()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AutoReplyRuleResponse> get(@PathVariable UUID id,
                                                     @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(autoReplyService.getRuleById(id, clientId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<AutoReplyRuleResponse> update(@PathVariable UUID id,
                                                        @Valid @RequestBody AutoReplyRuleRequest request,
                                                        @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(autoReplyService.updateRule(id, request, clientId));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<AutoReplyRuleResponse> updateStatus(@PathVariable UUID id,
                                                              @Valid @RequestBody AutoReplyRuleStatusRequest request,
                                                              @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(autoReplyService.updateStatus(id, request.getActive(), clientId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @RequestHeader("X-Client-Id") UUID clientId) {
        autoReplyService.deleteRule(id, clientId);
        return ResponseEntity.noContent().build();
    }
}
