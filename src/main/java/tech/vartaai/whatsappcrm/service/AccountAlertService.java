package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tech.vartaai.whatsappcrm.dto.AccountAlertDto;
import tech.vartaai.whatsappcrm.entity.AccountAlert;
import tech.vartaai.whatsappcrm.entity.AccountAlert.AlertCategory;
import tech.vartaai.whatsappcrm.entity.AccountAlert.Severity;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.exception.ApiException;
import tech.vartaai.whatsappcrm.repository.AccountAlertRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AccountAlertService {

    private final AccountAlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    public AccountAlertService(AccountAlertRepository alertRepository,
                               ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Create and persist an alert from a Meta webhook event.
     */
    public AccountAlert createAlert(Client client, AlertCategory category, Severity severity,
                                     String title, String message, String metaEventField,
                                     JsonNode payload) {
        AccountAlert alert = new AccountAlert();
        alert.setClient(client);
        alert.setCategory(category);
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setMessage(message);
        alert.setMetaEventField(metaEventField);
        try {
            alert.setPayloadJson(payload != null ? objectMapper.writeValueAsString(payload) : null);
        } catch (Exception e) {
            log.warn("Failed to serialize alert payload", e);
        }
        alertRepository.save(alert);
        log.info("ALERT_CREATED clientId={} category={} severity={} title={}",
                client.getId(), category, severity, title);
        return alert;
    }

    public List<AccountAlertDto> getUnresolvedAlerts(UUID clientId) {
        return alertRepository.findByClient_IdAndResolvedFalseOrderByCreatedAtDesc(clientId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Page<AccountAlertDto> getAllAlerts(UUID clientId, Pageable pageable) {
        return alertRepository.findByClient_IdOrderByCreatedAtDesc(clientId, pageable)
                .map(this::toDto);
    }

    public long getUnresolvedCount(UUID clientId) {
        return alertRepository.countByClient_IdAndResolvedFalse(clientId);
    }

    public void resolveAlert(UUID alertId, UUID clientId) {
        AccountAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "Alert not found"));
        if (!alert.getClient().getId().equals(clientId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Alert does not belong to this client");
        }
        alert.setResolved(true);
        alertRepository.save(alert);
    }

    public void resolveAllAlerts(UUID clientId) {
        List<AccountAlert> alerts = alertRepository.findByClient_IdAndResolvedFalseOrderByCreatedAtDesc(clientId);
        alerts.forEach(a -> a.setResolved(true));
        alertRepository.saveAll(alerts);
    }

    @Transactional
    public void deleteAlerts(UUID clientId) {
        alertRepository.deleteAllByClient_Id(clientId);
    }

    private AccountAlertDto toDto(AccountAlert alert) {
        return AccountAlertDto.builder()
                .id(alert.getId().toString())
                .category(alert.getCategory().name())
                .severity(alert.getSeverity().name())
                .title(alert.getTitle())
                .message(alert.getMessage())
                .metaEventField(alert.getMetaEventField())
                .resolved(alert.isResolved())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
