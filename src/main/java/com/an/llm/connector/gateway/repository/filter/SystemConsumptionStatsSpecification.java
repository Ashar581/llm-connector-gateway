package com.an.llm.connector.gateway.repository.filter;

import com.an.llm.connector.gateway.entity.system.SystemConsumptionStatsEntity;
import com.an.llm.connector.gateway.enums.LlmModels;
import com.an.llm.connector.gateway.model.filter.TokenStatsFilterRequest;
import jakarta.persistence.criteria.Predicate;
import lombok.NonNull;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

@Deprecated
public class SystemConsumptionStatsSpecification {

    public static Specification<@NonNull SystemConsumptionStatsEntity> filter(TokenStatsFilterRequest filter) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getAgentName() != null && !filter.getAgentName().isBlank()) {
                predicates.add(
                        cb.equal(root.get("agentName"), filter.getAgentName())
                );
            }

            if (filter.getModelName() != null && !filter.getModelName().isBlank()) {
                predicates.add(
                        cb.equal(root.get("modelName"), LlmModels.getFromValue(filter.getModelName()))
                );
            }

            if (filter.getServer() != null && !filter.getServer().isBlank()) {
                predicates.add(
                        cb.equal(root.get("server"), filter.getServer())
                );
            }

            if (filter.getStartDate() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("createdAt"),
                                filter.getStartDate()
                        )
                );
            }

            if (filter.getEndDate() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("createdAt"),
                                filter.getEndDate()
                        )
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
