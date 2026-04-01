package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.McpServerRegistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface McpServerRegistryRepository extends JpaRepository<McpServerRegistry, UUID> {
    List<McpServerRegistry> findByActiveTrueOrderByNameAsc();
    Optional<McpServerRegistry> findBySlug(String slug);
}
