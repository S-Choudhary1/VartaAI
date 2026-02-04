package tech.vartaai.whatsappcrm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.service.DashboardService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin()
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(@RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(dashboardService.getStats(clientId));
    }
}

