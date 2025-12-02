package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.Campaign;

import java.util.List;
import java.util.UUID;
@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    List<Campaign> findByClient_Id(UUID clientId);
}


