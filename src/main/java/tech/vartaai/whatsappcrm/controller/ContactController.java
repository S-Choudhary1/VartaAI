package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.dto.ContactRequest;
import tech.vartaai.whatsappcrm.dto.ContactResponse;
import tech.vartaai.whatsappcrm.service.ContactService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    public ResponseEntity<ContactResponse> createContact(@Valid @RequestBody ContactRequest request,
                                                         @RequestHeader("X-Client-Id") UUID clientId) {
        ContactResponse response = contactService.createOrUpdateContact(request, clientId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContactResponse> updateContact(@PathVariable UUID id,
                                                         @Valid @RequestBody ContactRequest request,
                                                         @RequestHeader("X-Client-Id") UUID clientId) {
        ContactResponse response = contactService.updateContact(id, request, clientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ContactResponse>> getAllContacts(@RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(contactService.getAllContacts(clientId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContactResponse> getContactById(@PathVariable UUID id,
                                                          @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(contactService.getContactById(id, clientId));
    }

    @GetMapping("/phone/{phone}")
    public ResponseEntity<ContactResponse> getContactByPhone(@PathVariable String phone,
                                                             @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(contactService.getContactByPhone(phone, clientId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable UUID id,
                                              @RequestHeader("X-Client-Id") UUID clientId) {
        contactService.deleteContact(id, clientId);
        return ResponseEntity.noContent().build();
    }
}
