package tech.vartaai.whatsappcrm.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.dto.AccountAlertDto;
import tech.vartaai.whatsappcrm.dto.AdminStatsResponse;
import tech.vartaai.whatsappcrm.dto.ClientDto;
import tech.vartaai.whatsappcrm.dto.UserDto;
import tech.vartaai.whatsappcrm.entity.Campaign;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.repository.ClientRepository;
import tech.vartaai.whatsappcrm.service.AccountAlertService;
import tech.vartaai.whatsappcrm.service.AdminService;
import tech.vartaai.whatsappcrm.service.ProvisioningService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@Slf4j
@CrossOrigin()
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final AccountAlertService alertService;
    private final ProvisioningService provisioningService;
    private final ClientRepository clientRepository;

    public AdminController(AdminService adminService, AccountAlertService alertService,
                           ProvisioningService provisioningService, ClientRepository clientRepository) {
        this.adminService = adminService;
        this.alertService = alertService;
        this.provisioningService = provisioningService;
        this.clientRepository = clientRepository;
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getSystemStats() {
        return ResponseEntity.ok(adminService.getSystemStats());
    }

    @GetMapping("/campaigns")
    public ResponseEntity<List<Campaign>> getAllCampaigns() {
        return ResponseEntity.ok(adminService.getAllCampaigns());
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/clients/{clientId}/alerts")
    public ResponseEntity<List<AccountAlertDto>> getClientAlerts(@PathVariable UUID clientId) {
        return ResponseEntity.ok(alertService.getUnresolvedAlerts(clientId));
    }

    @PostMapping("/clients/{clientId}/refresh")
    public ResponseEntity<ClientDto> refreshClientData(@PathVariable UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found: " + clientId));
        provisioningService.refreshClientData(client);
        // Re-read after refresh
        client = clientRepository.findById(clientId).orElseThrow();
        ClientDto dto = client.toDto();
        dto.setUnresolvedAlertCount(alertService.getUnresolvedCount(clientId));
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/clients/{clientId}/ai-chatbot")
    public ResponseEntity<ClientDto> toggleAiChatbot(
            @PathVariable UUID clientId,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        log.info("ADMIN_TOGGLE_AI_CHATBOT clientId={} enabled={}", clientId, enabled);
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found: " + clientId));
        client.setAiChatbotEnabled(enabled);
        clientRepository.save(client);
        return ResponseEntity.ok(client.toDto());
    }

    @PostMapping("/clients/{clientId}/retry-provisioning")
    public ResponseEntity<ClientDto> retryClientProvisioning(@PathVariable UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found: " + clientId));
        client = provisioningService.provisionClient(client);
        ClientDto dto = client.toDto();
        dto.setUnresolvedAlertCount(alertService.getUnresolvedCount(clientId));
        return ResponseEntity.ok(dto);
    }
}
