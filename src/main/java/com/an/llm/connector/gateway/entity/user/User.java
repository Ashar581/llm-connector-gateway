package com.an.llm.connector.gateway.entity.user;

import com.an.llm.connector.gateway.util.AppUtils;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Column(nullable = false)
    private String firstName;
    @Column(nullable = false)
    private String lastName;
    @Column(unique = true, nullable = false)
    private String username;
    @Column(unique = true, nullable = false)
    private String email;
    private String phoneNumber;
    private String countryCode;
    @Column(nullable = false)
    private String password;
    private Boolean active;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
    //groups
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_group_mapping",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"),
            indexes = {
                    @Index(name = "idx_user_group_user", columnList = "user_id"),
                    @Index(name = "idx_user_group_group", columnList = "group_id"),
                    @Index(name = "idx_user_group_user_group", columnList = "user_id, group_id")
            }
    )
    private Set<Group> groups;
    //roles
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_role_mapping",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name="role_id"),
            indexes = {
                    @Index(name = "idx_user_role_user", columnList = "user_id"),
                    @Index(name = "idx_user_role_role", columnList = "role_id"),
                    @Index(name = "idx_user_role_user_role", columnList = "user_id, role_id")
            }
    )
    private Set<Role> roles;


    @PrePersist
    private void prePersist() {
        String currentUser = AppUtils.getLoggedInUser().getPrincipal() != null ? AppUtils.getLoggedInUser().getPrincipal() : "SYSTEM";
        this.createdBy = currentUser;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.updatedBy = currentUser;
        this.active = true;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedBy = AppUtils.getLoggedInUser().getPrincipal() != null ? AppUtils.getLoggedInUser().getPrincipal() : "SYSTEM";
        this.updatedAt = Instant.now();
    }

}
