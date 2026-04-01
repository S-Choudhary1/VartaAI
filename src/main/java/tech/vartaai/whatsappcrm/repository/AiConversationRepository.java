package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.AiConversation;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {
    Optional<AiConversation> findFirstByChatbotConfig_IdAndContactPhoneAndStatusOrderByLastMessageAtDesc(UUID chatbotId, String phone, String status);
    Page<AiConversation> findByChatbotConfig_IdOrderByLastMessageAtDesc(UUID chatbotId, Pageable pageable);
}
