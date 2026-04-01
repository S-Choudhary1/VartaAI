package tech.vartaai.whatsappcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mcp_connections",
        uniqueConstraints = @UniqueConstraint(columnNames = {"chatbot_config_id", "mcp_server_id"}))
@Data
public class McpConnection {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatbot_config_id", nullable = false)
    @JsonIgnore
    private AiChatbotConfig chatbotConfig;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mcp_server_id", nullable = false)
    private McpServerRegistry mcpServer;

    @JsonIgnore
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "credentials", columnDefinition = "jsonb")
    private String credentials = "{}"; // encrypted OAuth tokens, API keys

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private String config = "{}"; // business-specific config (store URL, etc.)

    @Column(name = "status", length = 50)
    private String status = "pending"; // pending, connected, error, expired

    @Column(name = "last_health_check")
    private OffsetDateTime lastHealthCheck;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
