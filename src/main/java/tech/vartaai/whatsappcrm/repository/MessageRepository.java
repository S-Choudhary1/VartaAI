package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.Message;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByCampaignId(UUID campaignId);
    List<Message> findByContactId(UUID contactId);
    List<Message> findByClient_Id(UUID clientId);
    List<Message> findByContactIdAndClient_Id(UUID contactId, UUID clientId);
}


