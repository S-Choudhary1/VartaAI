package tech.vartaai.whatsappcrm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.vartaai.whatsappcrm.entity.Campaign;
import tech.vartaai.whatsappcrm.entity.Message;
import tech.vartaai.whatsappcrm.service.CampaignService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns")
@CrossOrigin()
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping(value = "/upload-csv", consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("templateId") UUID templateId,
            @RequestParam(value = "scheduledAt", required = false) String scheduledAt,
            @RequestParam("uploadedBy") UUID uploadedBy,
            @RequestHeader("X-Client-Id") UUID clientId
    ) {
        OffsetDateTime sched = scheduledAt != null && !scheduledAt.isBlank() ? OffsetDateTime.parse(scheduledAt) : null;
        Campaign c = campaignService.uploadCsv(name, templateId, sched, uploadedBy, file, clientId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "campaignId", c.getId(),
                "name", c.getName(),
                "status", c.getStatus().name()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Campaign> getCampaign(@PathVariable UUID id, @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(campaignService.getCampaign(id, clientId));
    }

    @GetMapping
    public ResponseEntity<java.util.List<Campaign>> getAllCampaigns(@RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(campaignService.getAllCampaigns(clientId));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<Message>> getCampaignMessages(@PathVariable UUID id, @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(campaignService.getCampaignMessages(id, clientId));
    }

    @GetMapping("/{id}/responses/export")
    public ResponseEntity<byte[]> exportCampaignResponses(@PathVariable UUID id,
                                                          @RequestHeader("X-Client-Id") UUID clientId) {
        String csv = campaignService.exportCampaignResponsesCsv(id, clientId);
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"campaign-" + id + "-responses.csv\"");

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}


