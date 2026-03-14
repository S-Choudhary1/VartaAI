package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.dto.MetaTemplateListResponse;
import tech.vartaai.whatsappcrm.dto.TemplateRequest;
import tech.vartaai.whatsappcrm.dto.TemplateResponse;
import tech.vartaai.whatsappcrm.dto.TemplateV2Request;
import tech.vartaai.whatsappcrm.service.TemplateService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @GetMapping("/meta")
    public ResponseEntity<MetaTemplateListResponse> getTemplatesFromMeta(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String name,
            @RequestParam(required = false, name = "name_or_content") String nameOrContent,
            @RequestParam(required = false) String status) {
        Map<String, String> filters = new HashMap<>();
        filters.put("category", category);
        filters.put("content", content);
        filters.put("language", language);
        filters.put("name", name);
        filters.put("name_or_content", nameOrContent);
        filters.put("status", status);
        return ResponseEntity.ok(templateService.getTemplatesFromMeta(clientId, filters));
    }

    @PostMapping("/meta")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TemplateResponse> createTemplateOnMeta(@Valid @RequestBody TemplateV2Request request,
                                                                 @RequestHeader("X-Client-Id") UUID clientId) {
        TemplateResponse response = templateService.createTemplateOnMeta(request, clientId, UUID.randomUUID());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/meta/approved")
    public ResponseEntity<MetaTemplateListResponse> getApprovedTemplatesFromMeta(
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(templateService.getApprovedTemplatesFromInternal(clientId));
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

