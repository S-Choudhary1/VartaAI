package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.FlowStepHistory;

import java.util.List;
import java.util.UUID;

@Repository
public interface FlowStepHistoryRepository extends JpaRepository<FlowStepHistory, UUID> {

    List<FlowStepHistory> findByExecutionIdOrderByCreatedAtAsc(UUID executionId);
}
