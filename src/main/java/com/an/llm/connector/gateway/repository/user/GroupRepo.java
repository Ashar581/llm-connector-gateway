package com.an.llm.connector.gateway.repository.user;

import com.an.llm.connector.gateway.entity.user.Group;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupRepo extends JpaRepository<@NonNull Group,@NonNull Long> {
    boolean existsByCode(@NonNull String code);
    Optional<Group> findByCode(@NonNull String code);
    List<Group> findByCodeIn(List<String> codes);
}
