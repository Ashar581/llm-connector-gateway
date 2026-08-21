package com.an.llm.connector.gateway.entity.settings;


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
public class SettingsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String label;
    @Column(unique = true, nullable = false)
    private String routePath;
    private Set<String> roles;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    @PrePersist
    private void prePersist(){
        String currentUser = (AppUtils.getLoggedInUser().getPrincipal() == null || AppUtils.getLoggedInUser().getPrincipal().equals("Anonymous")) ? "SYSTEM" : AppUtils.getLoggedInUser().getPrincipal();
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
