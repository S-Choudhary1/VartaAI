package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.FlowExecution;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowExecutionRepository extends JpaRepository<FlowExecution, UUID> {

    Page<FlowExecution> findByFlow_IdAndClientIdOrderByStartedAtDesc(
            UUID flowId, UUID clientId, Pageable pageable);

    Optional<FlowExecution> findByIdAndClientId(UUID id, UUID clientId);

    @Query("SELECT e FROM FlowExecution e WHERE e.contactId = :contactId AND e.clientId = :clientId " +
           "AND e.status IN ('ACTIVE', 'WAITING')")
    List<FlowExecution> findActiveExecutionsForContact(
            @Param("contactId") UUID contactId, @Param("clientId") UUID clientId);

    @Query("SELECT e FROM FlowExecution e WHERE e.status = 'WAITING' AND e.resumeAfter IS NOT NULL " +
           "AND e.resumeAfter <= :now")
    List<FlowExecution> findTimedOutExecutions(@Param("now") OffsetDateTime now);

    @Query("SELECT e.currentNodeId, COUNT(e) FROM FlowExecution e WHERE e.flow.id = :flowId " +
           "AND e.clientId = :clientId GROUP BY e.currentNodeId")
    List<Object[]> countByCurrentNodeForFlow(@Param("flowId") UUID flowId, @Param("clientId") UUID clientId);

    List<FlowExecution> findByCampaignIdOrderByStartedAtAsc(UUID campaignId);

    @Query("SELECT e.status, COUNT(e) FROM FlowExecution e WHERE e.flow.id = :flowId " +
           "AND e.clientId = :clientId GROUP BY e.status")
    List<Object[]> countGroupedByStatus(@Param("flowId") UUID flowId, @Param("clientId") UUID clientId);

    @Query("SELECT e FROM FlowExecution e WHERE e.parentExecutionId = :parentId " +
           "AND e.status IN ('ACTIVE', 'WAITING')")
    List<FlowExecution> findActiveSubFlows(@Param("parentId") UUID parentExecutionId);

    @Query("SELECT e FROM FlowExecution e WHERE e.parentExecutionId = :parentId " +
           "AND e.status IN ('ACTIVE', 'WAITING') ORDER BY e.startedAt DESC")
    Optional<FlowExecution> findLatestActiveSubFlow(@Param("parentId") UUID parentExecutionId);
}
