package tech.vartaai.whatsappcrm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tech.vartaai.whatsappcrm.dto.AuthRequest;
import tech.vartaai.whatsappcrm.dto.AuthResponse;
import tech.vartaai.whatsappcrm.entity.User;
import tech.vartaai.whatsappcrm.repository.UserRepository;
import tech.vartaai.whatsappcrm.security.JwtUtil;

import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.repository.ClientRepository;

@Service
@Slf4j
public class AuthService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;
    
    @org.springframework.beans.factory.annotation.Value("${security.jwt.expirationSeconds}")
    private Long expirationSeconds;

    public AuthResponse login(AuthRequest request) {
        log.info("Attempting login for user: {}", request.getUsername());
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("Login failed: User not found - {}", request.getUsername());
                    return new RuntimeException("Invalid username or password");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: Invalid password - {}", request.getUsername());
            throw new RuntimeException("Invalid username or password");
        }

        String clientId = user.getClient() != null ? user.getClient().getId().toString() : null;
        log.info("Login successful for user: {}, ClientId: {}", request.getUsername(), clientId);

        String token = jwtUtil.generateToken(
                user.getUsername(),
                user.getRole().name(),
                user.getId().toString(),
                clientId
        );

        return new AuthResponse(
                token,
                expirationSeconds,
                new AuthResponse.UserInfo(
                    user.getId(), 
                    user.getUsername(), 
                    user.getRole().name(),
                    user.getClient() != null ? user.getClient().getId() : null
                )
        );
    }

    public User register(AuthRequest request) {
        log.info("Registering new user/client: {}", request.getUsername());
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            log.warn("Registration failed: Username already exists - {}", request.getUsername());
            throw new RuntimeException("Username already exists");
        }
        
        Client client;
        if (request.getClientName() != null && !request.getClientName().isEmpty()) {
            client = new Client();
            client.setName(request.getClientName());
            client = clientRepository.save(client);
            log.info("Created new client: {}", client.getName());
        } else if (request.getClientId() != null) {
             log.warn("Public registration with existing clientId is not allowed");
             throw new RuntimeException("Cannot join existing client via public registration");
        } else {
             throw new RuntimeException("Client Name is required");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(User.Role.ADMIN); // Creator of client is ADMIN
        user.setClient(client);
        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getUsername());
        return savedUser;
    }

    public User createUser(tech.vartaai.whatsappcrm.dto.UserCreateRequest request) {
        log.info("Creating user: {} for client: {}", request.getUsername(), request.getClientId());
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
             log.warn("User creation failed: Username exists");
            throw new RuntimeException("Username already exists");
        }

        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new RuntimeException("Client not found"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setClient(client);
        
        return userRepository.save(user);
    }
}
