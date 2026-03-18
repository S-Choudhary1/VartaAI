package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.FlowDefinition;

import java.util.List;
import java.util.UUID;

@Repository
public interface FlowDefinitionRepository extends JpaRepository<FlowDefinition, UUID> {
    List<FlowDefinition> findByClient_IdOrderByCreatedAtDesc(UUID clientId);
}
