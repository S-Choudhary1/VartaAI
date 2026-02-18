package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.repository.ClientRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@Slf4j
@CrossOrigin()
public class ClientController {

    private final ClientRepository clientRepository;

    public ClientController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<Client>> getAllClients() {
        return ResponseEntity.ok(clientRepository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Client> getClientById(@PathVariable UUID id) {
        UUID safeId = Objects.requireNonNull(id, "Client id is required");
        return clientRepository.findById(safeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Client> createClient(@Valid @RequestBody Client client) {
        // Basic validation/logic could be moved to service
        if (client.getId() == null) {
            client.setId(UUID.randomUUID());
        }
        Client saved = clientRepository.save(client);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Client> updateClient(@PathVariable UUID id, @Valid @RequestBody Client clientDetails) {
        UUID safeId = Objects.requireNonNull(id, "Client id is required");
        return clientRepository.findById(safeId).map(client -> {
            client.setName(clientDetails.getName());
            client.setPhoneNumberId(clientDetails.getPhoneNumberId());
            client.setWabaId(clientDetails.getWabaId());
            client.setLanguage(clientDetails.getLanguage());
            client.setAutoReplyEnabled(clientDetails.isAutoReplyEnabled());
            if (clientDetails.getAccessToken() != null && !clientDetails.getAccessToken().isEmpty()) {
                client.setAccessToken(clientDetails.getAccessToken());
            }
            return ResponseEntity.ok(clientRepository.save(client));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteClient(@PathVariable UUID id) {
        UUID safeId = Objects.requireNonNull(id, "Client id is required");
        if (clientRepository.existsById(safeId)) {
            clientRepository.deleteById(safeId);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

