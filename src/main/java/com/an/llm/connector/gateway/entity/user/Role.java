package com.an.llm.connector.gateway.entity.user;

import com.an.llm.connector.gateway.util.AppUtils;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "roles")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Column(unique = true, nullable = false)
    private String name;
    @Column(unique = true, nullable = false)
    private String code;
    private String description;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private Group group;

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
