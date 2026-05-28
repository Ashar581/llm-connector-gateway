package com.an.llm.connector.gateway.entity;

import com.an.llm.connector.gateway.util.AppUtils;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class AgentFileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String fileName;
    private String contentType;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
    @Lob
    private byte [] data;

    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;
    private String updatedBy;

    @Column(nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_configuration_id")
    private AgentConfigurationEntity agentConfiguration;

    @PrePersist
    private void prePersist(){
        String currentUser = AppUtils.getLoggedInUser().getPrincipal() == null ? "SYSTEM" : AppUtils.getLoggedInUser().getPrincipal();
        this.createdBy = currentUser;
        this.createdAt = Instant.now();
        this.updatedBy = currentUser;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    private void preUpdated(){
        this.updatedAt = Instant.now();
        this.updatedBy = AppUtils.getLoggedInUser().getPrincipal() == null ? "SYSTEM" : AppUtils.getLoggedInUser().getPrincipal();
    }
}
