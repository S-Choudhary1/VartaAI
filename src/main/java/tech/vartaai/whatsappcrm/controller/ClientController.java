package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.dto.ClientCreateRequest;
import tech.vartaai.whatsappcrm.dto.ClientDto;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.repository.ClientRepository;
import tech.vartaai.whatsappcrm.service.AccountAlertService;
import tech.vartaai.whatsappcrm.service.AdminService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/clients")
@Slf4j
@CrossOrigin()
public class ClientController {

    private final ClientRepository clientRepository;
    private final AdminService adminService;
    private final AccountAlertService alertService;

    public ClientController(ClientRepository clientRepository, AdminService adminService,
                            AccountAlertService alertService) {
        this.clientRepository = clientRepository;
        this.adminService = adminService;
        this.alertService = alertService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<ClientDto>> getAllClients() {
        List<ClientDto> clients = clientRepository.findAll().stream()
                .map(c -> {
                    ClientDto dto = c.toDto();
                    dto.setUnresolvedAlertCount(alertService.getUnresolvedCount(c.getId()));
                    return dto;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(clients);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientDto> getClientById(@PathVariable UUID id) {
        return clientRepository.findById(id)
                .map(Client::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ClientDto> createClient(@Valid @RequestBody ClientCreateRequest request) {
        ClientDto created = adminService.createClientWithAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ClientDto> updateClient(@PathVariable UUID id, @Valid @RequestBody Client clientDetails) {
        return clientRepository.findById(id).map(client -> {
            client.setName(clientDetails.getName());
            client.setPhoneNumberId(clientDetails.getPhoneNumberId());
            client.setWabaId(clientDetails.getWabaId());
            client.setLanguage(clientDetails.getLanguage());
            if (clientDetails.getAccessToken() != null && !clientDetails.getAccessToken().isEmpty()) {
                client.setAccessToken(clientDetails.getAccessToken());
            }
            return ResponseEntity.ok(clientRepository.save(client).toDto());
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteClient(@PathVariable UUID id) {
        if (clientRepository.existsById(id)) {
            clientRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

