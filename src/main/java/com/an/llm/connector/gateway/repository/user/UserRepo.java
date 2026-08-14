package com.an.llm.connector.gateway.repository.user;

import com.an.llm.connector.gateway.entity.user.User;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepo extends JpaRepository<@NonNull User,@NonNull Long> {
    Optional<User> findByUsername(@NonNull String username);
    Optional<User> findByEmail(@NonNull String email);
    List<User> findAllByActive(boolean active);
    boolean existsByEmail(@NonNull String email);
    boolean existsByUsername(@NonNull String username);

    @Query("SELECT u.password FROM User u WHERE u.username = :username OR u.email = :email")
    Optional<String> findPasswordByUsernameOrEmail(@Param("username") String username, @Param("email") String email);
}
