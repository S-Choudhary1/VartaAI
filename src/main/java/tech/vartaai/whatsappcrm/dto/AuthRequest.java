package tech.vartaai.whatsappcrm.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthRequest {
    
    @NotBlank(message = "Username is required")
    private String username;
    
    @NotBlank(message = "Password is required")
    private String password;

    private String clientName;
    private java.util.UUID clientId;

    public AuthRequest() {
    }

    public AuthRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public java.util.UUID getClientId() {
        return clientId;
    }

    public void setClientId(java.util.UUID clientId) {
        this.clientId = clientId;
    }
}
