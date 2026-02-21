package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.Template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemplateRepository extends JpaRepository<Template, UUID> {
    List<Template> findByClient_Id(UUID clientId);
    Optional<Template> findByClient_IdAndProviderTemplateId(UUID clientId, String providerTemplateId);
    List<Template> findByClient_IdAndName(UUID clientId, String name);
}


