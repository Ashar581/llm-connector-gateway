package com.an.llm.connector.gateway.entity.system;

import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.enums.LlmModels;
import com.an.llm.connector.gateway.enums.Source;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
        indexes = {
                @Index(name = "idx_created", columnList = "createdAt"),
                @Index(name = "idx_model_name_created", columnList = "modelName, createdAt"),
                @Index(name = "idx_agent_name_created", columnList = "agentName, createdAt"),
                @Index(name = "idx_model_server_createdAt", columnList = "modelName, server, createdAt"),
                @Index(name = "idx_agent_server_createdAt", columnList = "agentName, server, createdAt")
        }
)
public class SystemConsumptionStatsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private LlmModels modelName;

    private String agentName;

    @Enumerated(EnumType.STRING)
    private LlmCapability type;

    @Enumerated(EnumType.STRING)
    private Source source;

    @Column(nullable = false)
    private Integer promptTokens;

    @Column(nullable = false)
    private Integer completionTokens;

    private Integer totalTokens;
    private Long responseTimeInMs;
    @Column(nullable = false)
    private Instant createdAt;
    private String server;

    @PrePersist
    private void perPersist(){
        this.createdAt = Instant.now();
        this.setTotalTokens(this.promptTokens+this.completionTokens);
    }
}
