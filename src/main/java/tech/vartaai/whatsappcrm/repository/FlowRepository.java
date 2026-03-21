package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.Flow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowRepository extends JpaRepository<Flow, UUID> {

    Page<Flow> findByClient_IdOrderByCreatedAtDesc(UUID clientId, Pageable pageable);

    List<Flow> findByClient_IdOrderByCreatedAtDesc(UUID clientId);

    Optional<Flow> findByIdAndClient_Id(UUID id, UUID clientId);

    List<Flow> findByClient_IdAndStatusAndTriggerType(
            UUID clientId, Flow.FlowStatus status, Flow.TriggerType triggerType);

    long countByStatus(Flow.FlowStatus status);
}
