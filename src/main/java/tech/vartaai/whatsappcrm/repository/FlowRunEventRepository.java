package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.FlowRunEvent;

import java.util.List;
import java.util.UUID;

@Repository
public interface FlowRunEventRepository extends JpaRepository<FlowRunEvent, UUID> {
    List<FlowRunEvent> findByContactFlowRun_IdOrderByCreatedAtAsc(UUID flowRunId);
}
