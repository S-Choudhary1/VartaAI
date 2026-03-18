package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.ContactFlowRun;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContactFlowRunRepository extends JpaRepository<ContactFlowRun, UUID> {
    Optional<ContactFlowRun> findByClient_IdAndContactIdAndStatus(UUID clientId,
                                                                   String contactId,
                                                                   ContactFlowRun.RunStatus status);

    Optional<ContactFlowRun> findByClient_IdAndContactIdAndLastOutboundProviderMessageIdAndStatus(
            UUID clientId,
            String contactId,
            String providerMessageId,
            ContactFlowRun.RunStatus status);

    List<ContactFlowRun> findByCampaign_IdAndClient_Id(UUID campaignId, UUID clientId);
}
