package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.dto.TemplateRequest;
import tech.vartaai.whatsappcrm.dto.TemplateResponse;
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id, @RequestHeader("X-Client-Id") UUID clientId) {
        templateService.deleteTemplate(id, clientId);
        return ResponseEntity.noContent().build();
    }
}

