package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByCampaignId(UUID campaignId);
    List<Message> findByClient_Id(UUID clientId);
    Page<Message> findByContactIdAndClient_Id(UUID contactId, UUID clientId, Pageable pageable);
    Optional<Message> findByIdAndClient_Id(UUID id, UUID clientId);

    Optional<Message> findByProviderMessageId(String providerMsgId);

    boolean existsByProviderMessageId(String providerMsgId);

    List<Message> findByFlowExecutionId(UUID flowExecutionId);
}


