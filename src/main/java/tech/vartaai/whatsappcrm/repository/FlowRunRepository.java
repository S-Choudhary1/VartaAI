package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.FlowRun;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowRunRepository extends JpaRepository<FlowRun, UUID> {
    Optional<FlowRun> findByClient_IdAndContactIdAndStatus(UUID clientId, String contactId, FlowRun.RunStatus status);
    Optional<FlowRun> findByClient_IdAndContactIdAndLastOutboundProviderMessageIdAndStatus(
            UUID clientId, String contactId, String providerMessageId, FlowRun.RunStatus status);
    List<FlowRun> findByCampaign_Id(UUID campaignId);
}
