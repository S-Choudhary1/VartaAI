package tech.vartaai.whatsappcrm.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.entity.McpConnection;
import tech.vartaai.whatsappcrm.entity.McpServerRegistry;
import tech.vartaai.whatsappcrm.exception.ApiException;
import tech.vartaai.whatsappcrm.service.McpConnectionService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mcp")
@Slf4j
@CrossOrigin()
public class McpServerController {

    private final McpConnectionService mcpConnectionService;

    public McpServerController(McpConnectionService mcpConnectionService) {
        this.mcpConnectionService = mcpConnectionService;
    }

    // ─── Registry (browse available servers) ─────────────────────

    @GetMapping("/servers")
    public ResponseEntity<List<McpServerRegistry>> listServers() {
        return ResponseEntity.ok(mcpConnectionService.listAvailableServers());
    }

    @GetMapping("/servers/{id}")
    public ResponseEntity<McpServerRegistry> getServer(@PathVariable UUID id) {
        return ResponseEntity.ok(mcpConnectionService.getServer(id));
    }

    // ─── Connections (per business) ──────────────────────────────

    @GetMapping("/connections")
    public ResponseEntity<List<McpConnection>> listConnections(
            @RequestHeader("X-Client-Id") UUID clientId) {
        log.info("MCP_LIST_CONNECTIONS clientId={}", clientId);
        return ResponseEntity.ok(mcpConnectionService.getConnections(clientId));
    }

    @PostMapping("/connections")
    public ResponseEntity<McpConnection> connectServer(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestBody Map<String, Object> body) {
        String mcpServerIdStr = (String) body.get("mcpServerId");
        if (mcpServerIdStr == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "mcpServerId is required.");
        }
        UUID mcpServerId = UUID.fromString(mcpServerIdStr);

        @SuppressWarnings("unchecked")
        Map<String, String> credentials = (Map<String, String>) body.get("credentials");
        @SuppressWarnings("unchecked")
        Map<String, String> serverConfig = (Map<String, String>) body.get("config");

        log.info("MCP_CONNECT clientId={} serverId={}", clientId, mcpServerId);
        McpConnection connection = mcpConnectionService.connectServer(
                clientId, mcpServerId, credentials, serverConfig);
        return ResponseEntity.status(HttpStatus.CREATED).body(connection);
    }

    @PutMapping("/connections/{connectionId}")
    public ResponseEntity<McpConnection> updateConnection(
            @PathVariable UUID connectionId,
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, String> credentials = (Map<String, String>) body.get("credentials");
        @SuppressWarnings("unchecked")
        Map<String, String> serverConfig = (Map<String, String>) body.get("config");

        log.info("MCP_UPDATE_CONNECTION clientId={} connectionId={}", clientId, connectionId);
        return ResponseEntity.ok(
                mcpConnectionService.updateConnection(connectionId, clientId, credentials, serverConfig));
    }

    @DeleteMapping("/connections/{connectionId}")
    public ResponseEntity<Void> disconnectServer(
            @PathVariable UUID connectionId,
            @RequestHeader("X-Client-Id") UUID clientId) {
        log.info("MCP_DISCONNECT clientId={} connectionId={}", clientId, connectionId);
        mcpConnectionService.disconnectServer(connectionId, clientId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/connections/{connectionId}/test")
    public ResponseEntity<McpConnection> testConnection(
            @PathVariable UUID connectionId,
            @RequestHeader("X-Client-Id") UUID clientId) {
        log.info("MCP_TEST_CONNECTION clientId={} connectionId={}", clientId, connectionId);
        return ResponseEntity.ok(mcpConnectionService.testConnection(connectionId, clientId));
    }
}
