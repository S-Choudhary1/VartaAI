package tech.vartaai.whatsappcrm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.vartaai.whatsappcrm.dto.AdminStatsResponse;
import tech.vartaai.whatsappcrm.dto.ClientCreateRequest;
import tech.vartaai.whatsappcrm.dto.ClientDto;
import tech.vartaai.whatsappcrm.dto.UserDto;
import tech.vartaai.whatsappcrm.entity.Campaign;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Flow;
import tech.vartaai.whatsappcrm.entity.User;
import tech.vartaai.whatsappcrm.repository.*;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final MessageRepository messageRepository;
    private final CampaignRepository campaignRepository;
    private final FlowRepository flowRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(ClientRepository clientRepository,
                        UserRepository userRepository,
                        ContactRepository contactRepository,
                        MessageRepository messageRepository,
                        CampaignRepository campaignRepository,
                        FlowRepository flowRepository,
                        PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.contactRepository = contactRepository;
        this.messageRepository = messageRepository;
        this.campaignRepository = campaignRepository;
        this.flowRepository = flowRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ClientDto createClientWithAdmin(ClientCreateRequest request) {
        if (userRepository.findByUsername(request.getAdminUsername()).isPresent()) {
            throw new RuntimeException("Username already exists: " + request.getAdminUsername());
        }

        Client client = new Client();
        client.setName(request.getName());
        if (request.getLanguage() != null && !request.getLanguage().isBlank()) {
            client.setLanguage(request.getLanguage());
        }
        client = clientRepository.save(client);
        log.info("Created client: {} ({})", client.getName(), client.getId());

        User admin = new User();
        admin.setUsername(request.getAdminUsername());
        admin.setPasswordHash(passwordEncoder.encode(request.getAdminPassword()));
        admin.setRole(User.Role.ADMIN);
        admin.setClient(client);
        userRepository.save(admin);
        log.info("Created admin user: {} for client: {}", admin.getUsername(), client.getId());

        return client.toDto();
    }

    public AdminStatsResponse getSystemStats() {
        return AdminStatsResponse.builder()
                .totalClients(clientRepository.count())
                .totalContacts(contactRepository.count())
                .totalMessages(messageRepository.count())
                .totalCampaigns(campaignRepository.count())
                .activeFlows(flowRepository.countByStatus(Flow.FlowStatus.ACTIVE))
                .build();
    }

    public List<Campaign> getAllCampaigns() {
        return campaignRepository.findAll();
    }

    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> UserDto.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .role(user.getRole().name())
                        .clientId(user.getClient() != null ? user.getClient().getId() : null)
                        .clientName(user.getClient() != null ? user.getClient().getName() : null)
                        .createdAt(user.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
