package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.McpConnection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface McpConnectionRepository extends JpaRepository<McpConnection, UUID> {
    List<McpConnection> findByChatbotConfig_Id(UUID chatbotConfigId);
    List<McpConnection> findByChatbotConfig_IdAndStatus(UUID chatbotConfigId, String status);
    Optional<McpConnection> findByChatbotConfig_IdAndMcpServer_Id(UUID chatbotConfigId, UUID mcpServerId);
    Optional<McpConnection> findByIdAndClientId(UUID id, UUID clientId);
}
