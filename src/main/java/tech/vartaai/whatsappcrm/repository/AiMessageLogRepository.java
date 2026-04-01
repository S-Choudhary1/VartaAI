package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.AiMessageLog;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiMessageLogRepository extends JpaRepository<AiMessageLog, UUID> {
    List<AiMessageLog> findByConversation_IdOrderByCreatedAtAsc(UUID conversationId);
}
