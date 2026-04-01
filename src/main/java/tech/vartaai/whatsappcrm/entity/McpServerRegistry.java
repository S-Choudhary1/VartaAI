package tech.vartaai.whatsappcrm.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mcp_server_registry")
@Data
public class McpServerRegistry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "mcp_url", nullable = false, length = 500)
    private String mcpUrl;

    @Column(name = "auth_type", nullable = false, length = 50)
    private String authType; // oauth2, api_key, bearer_token, none

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auth_config", columnDefinition = "jsonb")
    private String authConfig = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities", columnDefinition = "jsonb")
    private String capabilities = "[]";

    @Column(name = "category", length = 100)
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_fields", columnDefinition = "jsonb")
    private String requiredFields = "[]";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_premium", nullable = false)
    private boolean premium = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
