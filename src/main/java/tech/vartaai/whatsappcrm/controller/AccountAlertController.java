package tech.vartaai.whatsappcrm.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.dto.AccountAlertDto;
import tech.vartaai.whatsappcrm.service.AccountAlertService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@Slf4j
@CrossOrigin()
public class AccountAlertController {

    private final AccountAlertService alertService;

    public AccountAlertController(AccountAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ResponseEntity<Page<AccountAlertDto>> getAllAlerts(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(alertService.getAllAlerts(clientId, PageRequest.of(page, size)));
    }

    @GetMapping("/unresolved")
    public ResponseEntity<List<AccountAlertDto>> getUnresolvedAlerts(
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(alertService.getUnresolvedAlerts(clientId));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getUnresolvedCount(
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(Map.of("count", alertService.getUnresolvedCount(clientId)));
    }

    @PostMapping("/{alertId}/resolve")
    public ResponseEntity<Void> resolveAlert(
            @PathVariable UUID alertId,
            @RequestHeader("X-Client-Id") UUID clientId) {
        alertService.resolveAlert(alertId, clientId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resolve-all")
    public ResponseEntity<Void> resolveAllAlerts(
            @RequestHeader("X-Client-Id") UUID clientId) {
        alertService.resolveAllAlerts(clientId);
        return ResponseEntity.ok().build();
    }
}
