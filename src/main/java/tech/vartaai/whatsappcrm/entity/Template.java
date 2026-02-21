package tech.vartaai.whatsappcrm.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "templates")
@Data
public class Template {

    /**
     * High-level template format maintained by this application.
     */
    public enum TemplateType {
        TEXT,
        MEDIA,
        INTERACTIVE,
        AUTHENTICATION,
        LOCATION,
        PRODUCT,
        CATALOG,
        CAROUSEL,
        FLOW,
        ORDER_DETAILS,
        CUSTOM;

        public static TemplateType fromValue(String value) {
            if (value == null || value.isBlank()) {
                return CUSTOM;
            }

            String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase();
            try {
                return TemplateType.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return CUSTOM;
            }
        }
    }

    public enum TemplateCategory {
        MARKETING,
        UTILITY,
        AUTHENTICATION,
        UNKNOWN;

        public static TemplateCategory fromValue(String value) {
            if (value == null || value.isBlank()) {
                return UNKNOWN;
            }

            String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase();
            try {
                return TemplateCategory.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return UNKNOWN;
            }
        }
    }

    public enum TemplateStatus {
        DRAFT,
        PENDING,
        IN_REVIEW,
        APPROVED,
        REJECTED,
        PAUSED,
        DISABLED,
        PENDING_DELETION,
        UNKNOWN;

        public static TemplateStatus fromValue(String value) {
            if (value == null || value.isBlank()) {
                return UNKNOWN;
            }

            String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase();
            try {
                return TemplateStatus.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return UNKNOWN;
            }
        }
    }

    public enum QualityRating {
        GREEN,
        YELLOW,
        RED,
        UNKNOWN;

        public static QualityRating fromValue(String value) {
            if (value == null || value.isBlank()) {
                return UNKNOWN;
            }

            String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase();
            try {
                return QualityRating.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return UNKNOWN;
            }
        }
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "provider_template_id", length = 200)
    private String providerTemplateId;

    /**
     * Language code for this template, e.g. en_US, hi_IN.
     * If null, the client's default language (if any) will be used.
     */
    @Column(name = "language_code", length = 32)
    private String languageCode;

    /**
     * High-level interaction category for this template
     * (e.g. CHOICE, EXTERNAL_LINK). This is for app/UI logic
     * and does not affect how Meta stores the template.
     */
    @Column(name = "interaction_type", length = 50)
    private String interactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private TemplateType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private TemplateCategory category = TemplateCategory.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private TemplateStatus status = TemplateStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_rating", length = 20)
    private QualityRating qualityRating = QualityRating.UNKNOWN;

    @Column(name = "allow_category_change")
    private Boolean allowCategoryChange;

    @Column(name = "content", nullable = false)
    private String contentJson;

    @Type(JsonBinaryType.class)
    @Column(name = "components", columnDefinition = "jsonb")
    private String componentsJson;

    @Type(JsonBinaryType.class)
    @Column(name = "example_values", columnDefinition = "jsonb")
    private String exampleValuesJson;

    @Type(JsonBinaryType.class)
    @Column(name = "raw_template", columnDefinition = "jsonb")
    private String rawTemplateJson;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}


