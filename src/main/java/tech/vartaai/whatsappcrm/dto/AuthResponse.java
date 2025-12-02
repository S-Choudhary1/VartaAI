package tech.vartaai.whatsappcrm.dto;

import java.util.UUID;

public class AuthResponse {
    
    private String accessToken;
    private String tokenType = "Bearer";
    private Long expiresIn;
    private UserInfo user;

    public AuthResponse() {
    }

    public AuthResponse(String accessToken, Long expiresIn, UserInfo user) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UserInfo getUser() {
        return user;
    }

    public void setUser(UserInfo user) {
        this.user = user;
    }

    public static class UserInfo {
        private UUID id;
        private String username;
        private String role;
        private UUID clientId;

        public UserInfo() {
        }

        public UserInfo(UUID id, String username, String role, UUID clientId) {
            this.id = id;
            this.username = username;
            this.role = role;
            this.clientId = clientId;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public UUID getClientId() {
            return clientId;
        }

        public void setClientId(UUID clientId) {
            this.clientId = clientId;
        }
    }
}
