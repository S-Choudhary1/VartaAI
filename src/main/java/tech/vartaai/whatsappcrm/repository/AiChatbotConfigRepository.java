package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.AiChatbotConfig;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiChatbotConfigRepository extends JpaRepository<AiChatbotConfig, UUID> {
    Optional<AiChatbotConfig> findByClient_Id(UUID clientId);
    Optional<AiChatbotConfig> findByClient_IdAndEnabledTrue(UUID clientId);
}
