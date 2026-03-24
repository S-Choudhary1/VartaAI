package tech.vartaai.whatsappcrm.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.dto.AccountAlertDto;
import tech.vartaai.whatsappcrm.dto.AdminStatsResponse;
import tech.vartaai.whatsappcrm.dto.UserDto;
import tech.vartaai.whatsappcrm.entity.Campaign;
import tech.vartaai.whatsappcrm.service.AccountAlertService;
import tech.vartaai.whatsappcrm.service.AdminService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@Slf4j
@CrossOrigin()
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final AccountAlertService alertService;

    public AdminController(AdminService adminService, AccountAlertService alertService) {
        this.adminService = adminService;
        this.alertService = alertService;
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
}
