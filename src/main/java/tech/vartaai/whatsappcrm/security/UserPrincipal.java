package tech.vartaai.whatsappcrm.security;

import java.util.UUID;

public class UserPrincipal {
    private final UUID id;
    private final String username;
    private final UUID clientId;
    private final String role;

    public UserPrincipal(String id, String username, String clientId, String role) {
        this.id = id != null ? UUID.fromString(id) : null;
        this.username = username;
        this.clientId = clientId != null ? UUID.fromString(clientId) : null;
        this.role = role;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public UUID getClientId() { return clientId; }
    public String getRole() { return role; }
}

