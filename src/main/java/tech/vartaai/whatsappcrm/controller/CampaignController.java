package tech.vartaai.whatsappcrm.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.vartaai.whatsappcrm.dto.CampaignDto;
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
            @RequestParam(value = "flowId", required = false) UUID flowId,
            @RequestHeader("X-Client-Id") UUID clientId
    ) {
        OffsetDateTime sched = scheduledAt != null && !scheduledAt.isBlank() ? OffsetDateTime.parse(scheduledAt) : null;
        Campaign c = campaignService.uploadCsv(name, templateId, sched, uploadedBy, file, clientId, flowId);
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
    public ResponseEntity<Page<CampaignDto>> getAllCampaigns(
            @RequestHeader("X-Client-Id") UUID clientId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(campaignService.getAllCampaigns(clientId, pageable));
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


