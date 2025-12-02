package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.vartaai.whatsappcrm.dto.ContactRequest;
import tech.vartaai.whatsappcrm.dto.ContactResponse;
import tech.vartaai.whatsappcrm.entity.Contact;
import tech.vartaai.whatsappcrm.repository.ContactRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ContactService {

    private final ContactRepository contactRepository;
    private final ObjectMapper objectMapper;

    public ContactService(ContactRepository contactRepository, ObjectMapper objectMapper) {
        this.contactRepository = contactRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ContactResponse createOrUpdateContact(ContactRequest request, UUID clientId) {
        // Need to fetch client entity or just use ID?
        // Ideally we should have a getReference or ensure client exists.
        // Assuming client exists since it passed Interceptor.
        
        // Use findByPhoneAndClient_Id
        Contact contact = contactRepository.findByPhoneAndClient_Id(request.getPhone(), clientId)
                .orElse(new Contact());

        if (contact.getClient() == null) {
            tech.vartaai.whatsappcrm.entity.Client clientRef = new tech.vartaai.whatsappcrm.entity.Client();
            clientRef.setId(clientId);
            contact.setClient(clientRef);
        }

        contact.setPhone(request.getPhone());
        contact.setName(request.getName());

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            contact.setTags(String.join(",", request.getTags()));
        }

        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            try {
                contact.setMetadataJson(objectMapper.writeValueAsString(request.getMetadata()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize metadata", e);
            }
        }

        Contact saved = contactRepository.save(contact);
        return toResponse(saved);
    }

    public ContactResponse getContactById(UUID id, UUID clientId) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact not found"));
        
        if (!contact.getClient().getId().equals(clientId)) {
             throw new RuntimeException("Contact not found"); // mask for security
        }

        return toResponse(contact);
    }

    public List<ContactResponse> getAllContacts(UUID clientId) {
        return contactRepository.findByClient_Id(clientId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ContactResponse getContactByPhone(String phone, UUID clientId) {
        Contact contact = contactRepository.findByPhoneAndClient_Id(phone, clientId)
                .orElseThrow(() -> new RuntimeException("Contact not found"));
        return toResponse(contact);
    }

    @Transactional
    public ContactResponse updateContact(UUID id, ContactRequest request, UUID clientId) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact not found"));

        if (!contact.getClient().getId().equals(clientId)) {
             throw new RuntimeException("Contact not found");
        }

        contact.setName(request.getName());
        contact.setPhone(request.getPhone());

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            contact.setTags(String.join(",", request.getTags()));
        }

        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            try {
                contact.setMetadataJson(objectMapper.writeValueAsString(request.getMetadata()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize metadata", e);
            }
        }

        Contact saved = contactRepository.save(contact);
        return toResponse(saved);
    }

    @Transactional
    public void deleteContact(UUID id, UUID clientId) {
        Contact contact = contactRepository.findById(id)
             .orElseThrow(() -> new RuntimeException("Contact not found"));
             
        if (!contact.getClient().getId().equals(clientId)) {
             throw new RuntimeException("Contact not found");
        }
        contactRepository.delete(contact);
    }

    private ContactResponse toResponse(Contact contact) {
        List<String> tags = contact.getTags() != null && !contact.getTags().isEmpty()
                ? Arrays.asList(contact.getTags().split(","))
                : List.of();

        try {
            return new ContactResponse(
                    contact.getId(),
                    contact.getPhone(),
                    contact.getName(),
                    tags,
                    contact.getMetadataJson() != null 
                            ? objectMapper.readValue(contact.getMetadataJson(), Map.class)
                            : Map.of()
            );
        } catch (JsonProcessingException e) {
            return new ContactResponse(
                    contact.getId(),
                    contact.getPhone(),
                    contact.getName(),
                    tags,
                    Map.of()
            );
        }
    }
}
