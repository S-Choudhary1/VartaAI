package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.dto.Status;
import tech.vartaai.whatsappcrm.entity.Campaign;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    List<Campaign> findByClient_IdOrderByCreatedAtDesc(UUID clientId);
    Page<Campaign> findByClient_IdOrderByCreatedAtDesc(UUID clientId, Pageable pageable);

    @Query("SELECT c FROM Campaign c WHERE c.status = :status AND (c.scheduledAt IS NULL OR c.scheduledAt <= :now)")
    List<Campaign> findReadyToRun(@Param("status") Status status, @Param("now") OffsetDateTime now);
}
