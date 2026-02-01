package com.heronixedu.hub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 255)
    private String email;

    // Legacy role field (kept for backwards compatibility)
    @Column(nullable = false, length = 20)
    private String role;

    // New role relationship
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role roleEntity;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public User(String username, String passwordHash, String fullName, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.isActive = true;
    }

    // Helper method to check if user has a specific permission
    public boolean hasPermission(String permissionName) {
        if (roleEntity != null) {
            return roleEntity.hasPermission(permissionName);
        }
        // Fallback to legacy role check for SUPERADMIN
        return "SUPERADMIN".equals(role);
    }

    // Helper method to check if user can access admin panel
    public boolean canAccessAdminPanel() {
        if (roleEntity != null) {
            return roleEntity.hasPermission("CAN_MANAGE_USERS") ||
                    roleEntity.hasPermission("CAN_MANAGE_PRODUCTS") ||
                    roleEntity.hasPermission("CAN_CONFIGURE_NETWORK") ||
                    roleEntity.hasPermission("CAN_VIEW_LOGS");
        }
        // Fallback for legacy roles
        return "SUPERADMIN".equals(role) || "IT_ADMIN".equals(role);
    }
}
