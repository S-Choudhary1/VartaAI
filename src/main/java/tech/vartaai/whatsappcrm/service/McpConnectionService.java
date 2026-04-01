package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tech.vartaai.whatsappcrm.entity.AiChatbotConfig;
import tech.vartaai.whatsappcrm.entity.McpConnection;
import tech.vartaai.whatsappcrm.entity.McpServerRegistry;
import tech.vartaai.whatsappcrm.exception.ApiException;
import tech.vartaai.whatsappcrm.repository.AiChatbotConfigRepository;
import tech.vartaai.whatsappcrm.repository.McpConnectionRepository;
import tech.vartaai.whatsappcrm.repository.McpServerRegistryRepository;
import tech.vartaai.whatsappcrm.util.CredentialEncryption;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
public class McpConnectionService {

    private final McpServerRegistryRepository serverRegistryRepository;
    private final McpConnectionRepository connectionRepository;
    private final AiChatbotConfigRepository chatbotConfigRepository;
    private final CredentialEncryption credentialEncryption;
    private final ObjectMapper objectMapper;

    public McpConnectionService(McpServerRegistryRepository serverRegistryRepository,
                                McpConnectionRepository connectionRepository,
                                AiChatbotConfigRepository chatbotConfigRepository,
                                CredentialEncryption credentialEncryption,
                                ObjectMapper objectMapper) {
        this.serverRegistryRepository = serverRegistryRepository;
        this.connectionRepository = connectionRepository;
        this.chatbotConfigRepository = chatbotConfigRepository;
        this.credentialEncryption = credentialEncryption;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════════
    //  MCP SERVER REGISTRY
    // ═══════════════════════════════════════════════════════════════

    public List<McpServerRegistry> listAvailableServers() {
        return serverRegistryRepository.findByActiveTrueOrderByNameAsc();
    }

    public McpServerRegistry getServer(UUID serverId) {
        return serverRegistryRepository.findById(serverId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MCP_SERVER_NOT_FOUND",
                        "MCP server not found."));
    }

    // ═══════════════════════════════════════════════════════════════
    //  CONNECTION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    public List<McpConnection> getConnections(UUID clientId) {
        AiChatbotConfig config = chatbotConfigRepository.findByClient_Id(clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONFIG_NOT_FOUND",
                        "AI chatbot config not found."));
        return connectionRepository.findByChatbotConfig_Id(config.getId());
    }

    public McpConnection connectServer(UUID clientId, UUID mcpServerId,
                                        Map<String, String> credentials,
                                        Map<String, String> serverConfig) {
        AiChatbotConfig config = chatbotConfigRepository.findByClient_Id(clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONFIG_NOT_FOUND",
                        "AI chatbot config not found. Create one first."));

        McpServerRegistry server = serverRegistryRepository.findById(mcpServerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MCP_SERVER_NOT_FOUND",
                        "MCP server not found."));

        // Check for existing connection
        Optional<McpConnection> existing = connectionRepository
                .findByChatbotConfig_IdAndMcpServer_Id(config.getId(), mcpServerId);
        if (existing.isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CONNECTED",
                    "This MCP server is already connected.");
        }

        // Validate required fields
        validateRequiredFields(server, credentials, serverConfig);

        // Encrypt credentials
        String encryptedCredentials = encryptCredentials(credentials);

        McpConnection connection = new McpConnection();
        connection.setClientId(clientId);
        connection.setChatbotConfig(config);
        connection.setMcpServer(server);
        connection.setCredentials(encryptedCredentials);
        connection.setStatus("connected");
        connection.setLastHealthCheck(OffsetDateTime.now());

        if (serverConfig != null && !serverConfig.isEmpty()) {
            try {
                connection.setConfig(objectMapper.writeValueAsString(serverConfig));
            } catch (Exception e) {
                log.warn("Failed to serialize server config: {}", e.getMessage());
            }
        }

        McpConnection saved = connectionRepository.save(connection);
        log.info("MCP_CONNECTED clientId={} serverId={} serverSlug={}", clientId, mcpServerId, server.getSlug());
        return saved;
    }

    public McpConnection updateConnection(UUID connectionId, UUID clientId,
                                           Map<String, String> credentials,
                                           Map<String, String> serverConfig) {
        McpConnection connection = connectionRepository.findByIdAndClientId(connectionId, clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONNECTION_NOT_FOUND",
                        "MCP connection not found."));

        if (credentials != null && !credentials.isEmpty()) {
            connection.setCredentials(encryptCredentials(credentials));
        }
        if (serverConfig != null) {
            try {
                connection.setConfig(objectMapper.writeValueAsString(serverConfig));
            } catch (Exception e) {
                log.warn("Failed to serialize server config: {}", e.getMessage());
            }
        }

        connection.setStatus("connected");
        connection.setErrorMessage(null);
        connection.setLastHealthCheck(OffsetDateTime.now());
        return connectionRepository.save(connection);
    }

    public void disconnectServer(UUID connectionId, UUID clientId) {
        McpConnection connection = connectionRepository.findByIdAndClientId(connectionId, clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONNECTION_NOT_FOUND",
                        "MCP connection not found."));
        connectionRepository.delete(connection);
        log.info("MCP_DISCONNECTED clientId={} connectionId={} serverSlug={}",
                clientId, connectionId, connection.getMcpServer().getSlug());
    }

    public McpConnection testConnection(UUID connectionId, UUID clientId) {
        McpConnection connection = connectionRepository.findByIdAndClientId(connectionId, clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONNECTION_NOT_FOUND",
                        "MCP connection not found."));

        // Simple health check — verify credentials exist and mark as checked
        try {
            String creds = connection.getCredentials();
            if (creds == null || creds.isBlank() || "{}".equals(creds)) {
                connection.setStatus("error");
                connection.setErrorMessage("No credentials configured.");
            } else {
                connection.setStatus("connected");
                connection.setErrorMessage(null);
            }
            connection.setLastHealthCheck(OffsetDateTime.now());
            return connectionRepository.save(connection);
        } catch (Exception e) {
            connection.setStatus("error");
            connection.setErrorMessage(e.getMessage());
            connection.setLastHealthCheck(OffsetDateTime.now());
            return connectionRepository.save(connection);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  MCP SERVERS FOR CLAUDE API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns the list of active MCP server configs formatted for the Claude API
     * mcp_servers parameter.
     */
    public List<Map<String, Object>> getActiveMcpServersForApi(UUID chatbotConfigId) {
        List<McpConnection> connections = connectionRepository
                .findByChatbotConfig_IdAndStatus(chatbotConfigId, "connected");

        List<Map<String, Object>> mcpServers = new ArrayList<>();
        for (McpConnection conn : connections) {
            McpServerRegistry server = conn.getMcpServer();
            Map<String, Object> mcpServer = new LinkedHashMap<>();
            mcpServer.put("type", "url");
            mcpServer.put("url", server.getMcpUrl());
            mcpServer.put("name", server.getSlug());

            // Decrypt credentials and extract auth token
            String authToken = extractAuthToken(conn);
            if (authToken != null) {
                mcpServer.put("authorization_token", authToken);
            }

            mcpServers.add(mcpServer);
        }
        return mcpServers;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void validateRequiredFields(McpServerRegistry server,
                                         Map<String, String> credentials,
                                         Map<String, String> serverConfig) {
        if ("api_key".equals(server.getAuthType())) {
            // For API key auth, credentials must contain the key(s)
            if (credentials == null || credentials.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CREDENTIALS_REQUIRED",
                        "API key credentials are required for this server.");
            }
        }

        // Validate required_fields from server registry
        if (server.getRequiredFields() != null && !"[]".equals(server.getRequiredFields())) {
            try {
                List<Map<String, String>> requiredFields = objectMapper.readValue(
                        server.getRequiredFields(), new TypeReference<>() {});
                for (Map<String, String> field : requiredFields) {
                    String key = field.get("key");
                    if (key != null) {
                        boolean inCredentials = credentials != null && credentials.containsKey(key);
                        boolean inConfig = serverConfig != null && serverConfig.containsKey(key);
                        if (!inCredentials && !inConfig) {
                            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_FIELD",
                                    "Required field '" + key + "' is missing.");
                        }
                    }
                }
            } catch (ApiException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Failed to parse required fields: {}", e.getMessage());
            }
        }
    }

    private String encryptCredentials(Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty()) return "{}";
        try {
            String json = objectMapper.writeValueAsString(credentials);
            return credentialEncryption.encrypt(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt credentials", e);
        }
    }

    private String extractAuthToken(McpConnection connection) {
        try {
            String decrypted = credentialEncryption.decrypt(connection.getCredentials());
            if (decrypted == null || decrypted.isBlank() || "{}".equals(decrypted)) return null;

            Map<String, String> creds = objectMapper.readValue(decrypted, new TypeReference<>() {});

            // Try common token field names
            String token = creds.get("access_token");
            if (token == null) token = creds.get("api_key");
            if (token == null) token = creds.get("bearer_token");
            if (token == null) token = creds.get("token");

            return token;
        } catch (Exception e) {
            log.warn("Failed to extract auth token for connection {}: {}", connection.getId(), e.getMessage());
            return null;
        }
    }
}
