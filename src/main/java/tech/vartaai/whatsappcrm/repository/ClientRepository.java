package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Client.OnboardingStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {
    Optional<Client> findByPhoneNumberId(String phoneNumberId);
    Optional<Client> findByWabaId(String wabaId);

    List<Client> findByOnboardingStatus(OnboardingStatus status);

    @Query("SELECT c FROM Client c WHERE c.accessToken IS NOT NULL " +
           "AND c.tokenExpiresAt IS NOT NULL " +
           "AND c.tokenExpiresAt < :threshold")
    List<Client> findClientsWithExpiringTokens(@Param("threshold") OffsetDateTime threshold);
}

