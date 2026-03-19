package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.Contact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContactRepository extends JpaRepository<Contact, UUID> {
    Optional<Contact> findByPhoneAndClient_Id(String phone, UUID clientId);
    List<Contact> findByClient_Id(UUID clientId);
    Page<Contact> findByClient_Id(UUID clientId, Pageable pageable);
    // Deprecated or removed: findByPhone - phone is not unique globally anymore
}


