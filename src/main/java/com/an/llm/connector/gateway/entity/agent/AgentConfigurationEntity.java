package com.an.llm.connector.gateway.entity.agent;

import com.an.llm.connector.gateway.enums.ClassificationMode;
import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.enums.LlmModels;
import com.an.llm.connector.gateway.enums.Source;
import com.an.llm.connector.gateway.model.classification.DocumentTypeDefinition;
import com.an.llm.connector.gateway.util.AppUtils;
import com.an.llm.connector.gateway.util.TimeUtils;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@AllArgsConstructor
@Table(
        indexes = {
                @Index(name = "idx_name_active_created", columnList = "name,active,createdBy")
        }
)
@RequiredArgsConstructor
public class AgentConfigurationEntity implements TimeUtils {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Source source;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LlmCapability type;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LlmModels model;

    @Column(columnDefinition = "TEXT",nullable = false)
    private String instructions;
    private String description;

    @OneToMany(mappedBy = "agentConfiguration",cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<AgentFileEntity> files;

    @Column(unique = true)
    private UUID uniqueId;

    @Column(unique = true)
    private String name;

    private Boolean active;
    private Double temperature;
    private Integer maxTokens;
    private Boolean isPrivate;

    @Enumerated(EnumType.STRING)
    private ClassificationMode classificationMode;
    @JdbcTypeCode(SqlTypes.JSON)
    private List<DocumentTypeDefinition> documentTypes;
    private Integer pageChunk;

    //RAG based keys.
    private String vectorStore;
    private String encodingType;
    private Integer chunkSize;
    private Integer minChunkLengthToEmbed;
    private Integer minChunkSizeChars;
    private Integer maxNumChunks;
    private Boolean separator;

    private Integer topK;
    private Double similarityThreshold;

    private Boolean enablePrivateMode;

    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;
    private String updatedBy;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void prePersist() {
        String currentUser = AppUtils.getLoggedInUser().getPrincipal() == null ? "SYSTEM" : AppUtils.getLoggedInUser().getPrincipal();
        this.createdBy = currentUser;
        this.createdAt = Instant.now();
        this.updatedBy = currentUser;
        this.updatedAt = Instant.now();
        this.active = active != null && active;
        this.name = "agent_" + getLlmConfigUtcPostfix();
        this.temperature = this.temperature == null ? 0.0 : this.temperature;
        this.uniqueId = UUID.randomUUID();
        this.isPrivate = this.isPrivate != null && this.isPrivate;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = Instant.now();
        this.updatedBy = AppUtils.getLoggedInUser().getPrincipal() == null ? "SYSTEM" : AppUtils.getLoggedInUser().getPrincipal();
    }
}
