package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.FlowTransition;

import java.util.List;
import java.util.UUID;

@Repository
public interface FlowTransitionRepository extends JpaRepository<FlowTransition, UUID> {
    List<FlowTransition> findByFlowVersion_Id(UUID flowVersionId);
    List<FlowTransition> findByFlowVersion_IdAndFromStepKey(UUID flowVersionId, String fromStepKey);
    void deleteByFlowVersion_Id(UUID flowVersionId);
}
