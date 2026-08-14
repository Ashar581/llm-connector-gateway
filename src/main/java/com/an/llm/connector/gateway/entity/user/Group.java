package com.an.llm.connector.gateway.entity.user;

import com.an.llm.connector.gateway.util.AppUtils;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "groups")
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Column(unique = true)
    private String name;
    @Column(unique = true)
    private String code;
    private String description;

    @OneToMany(mappedBy = "group")
    private List<Role> roles;

    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    @PrePersist
    private void prePersist() {
        String currentUser = AppUtils.getLoggedInUser().getPrincipal() == null ? "SYSTEM" : AppUtils.getLoggedInUser().getPrincipal();
        this.createdBy = currentUser;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.updatedBy = currentUser;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = Instant.now();
        this.updatedBy = AppUtils.getLoggedInUser().getPrincipal() == null ? "SYSTEM" : AppUtils.getLoggedInUser().getPrincipal();
    }
}
