package com.an.llm.connector.gateway.repository.user;

import com.an.llm.connector.gateway.entity.user.Role;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepo extends JpaRepository<@NonNull Role,@NonNull Long> {
    boolean existsByCode(@NonNull String code);
    Optional<Role> findByCode(@NonNull String code);
    List<Role> findByCodeIn(List<String> codes);
}
