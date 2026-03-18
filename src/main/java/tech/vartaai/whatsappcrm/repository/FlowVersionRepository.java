package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.FlowVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowVersionRepository extends JpaRepository<FlowVersion, UUID> {
    List<FlowVersion> findByFlowDefinition_IdOrderByVersionNumberDesc(UUID flowDefinitionId);
    Optional<FlowVersion> findByFlowDefinition_IdAndVersionNumber(UUID flowDefinitionId, Integer versionNumber);
}
