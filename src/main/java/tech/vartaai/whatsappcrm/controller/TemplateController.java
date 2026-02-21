package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.dto.MetaTemplateResponse;
import tech.vartaai.whatsappcrm.dto.TemplateRequest;
import tech.vartaai.whatsappcrm.dto.TemplateResponse;
import tech.vartaai.whatsappcrm.dto.TemplateV2Request;
import tech.vartaai.whatsappcrm.service.TemplateService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/templates")
@CrossOrigin()
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<TemplateResponse> createTemplate(@Valid @RequestBody TemplateRequest request,
                                                           @RequestHeader("X-Client-Id") UUID clientId) {
        TemplateResponse response = templateService.createTemplate(request, UUID.randomUUID(), clientId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<TemplateResponse>> getAllTemplates(@RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(templateService.getAllTemplates(clientId));
    }

    @GetMapping("/meta/approved")
    public ResponseEntity<List<MetaTemplateResponse>> getApprovedTemplatesFromMeta(
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(templateService.getApprovedTemplatesFromMeta(clientId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> getTemplateById(@PathVariable UUID id, @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(templateService.getTemplateById(id, clientId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<TemplateResponse> updateTemplate(@PathVariable UUID id, 
                                                           @Valid @RequestBody TemplateRequest request,
                                                           @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(templateService.updateTemplate(id, request, clientId));
    }

    @PostMapping("/v2")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<TemplateResponse> createTemplateV2(@Valid @RequestBody TemplateV2Request request,
                                                             @RequestHeader("X-Client-Id") UUID clientId) {
        TemplateResponse response = templateService.createTemplateV2(request, UUID.randomUUID(), clientId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/v2/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<TemplateResponse> updateTemplateV2(@PathVariable UUID id,
                                                             @Valid @RequestBody TemplateV2Request request,
                                                             @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(templateService.updateTemplateV2(id, request, clientId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id, @RequestHeader("X-Client-Id") UUID clientId) {
        templateService.deleteTemplate(id, clientId);
        return ResponseEntity.noContent().build();
    }
}

