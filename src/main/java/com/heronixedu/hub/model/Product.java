package com.heronixedu.hub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", unique = true, nullable = false, length = 50)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "executable_path", nullable = false)
    private String executablePath;

    @Column(name = "is_installed")
    private Boolean isInstalled = false;

    @Column(name = "last_launched")
    private LocalDateTime lastLaunched;

    // New fields for version tracking and deployment
    @Column(name = "current_version", length = 50)
    private String currentVersion;

    @Column(name = "latest_version", length = 50)
    private String latestVersion;

    @Column(name = "install_path", length = 500)
    private String installPath;

    @Column(name = "icon_path", length = 255)
    private String iconPath;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String category;

    @Column(name = "requires_admin")
    private Boolean requiresAdmin = false;

    // Role-based access control
    // Comma-separated list of roles that can see this product (e.g., "SUPERADMIN,IT_ADMIN,TEACHER")
    // If null or empty, product is visible to all roles
    @Column(name = "allowed_roles", length = 500)
    private String allowedRoles;

    // Comma-separated list of roles that are explicitly excluded from seeing this product
    @Column(name = "excluded_roles", length = 500)
    private String excludedRoles;

    // Whether this is a system/admin-only application (like Guardian Monitor, SIS Server)
    @Column(name = "is_system_app")
    private Boolean isSystemApp = false;

    public Product(String productCode, String productName, String executablePath) {
        this.productCode = productCode;
        this.productName = productName;
        this.executablePath = executablePath;
        this.isInstalled = false;
        this.currentVersion = "1.0.0";
    }

    public boolean hasUpdate() {
        if (latestVersion == null || currentVersion == null) {
            return false;
        }
        return !latestVersion.equals(currentVersion);
    }

    /**
     * Check if a user with the given role can access this product.
     * @param userRole the role name (e.g., "TEACHER", "STUDENT", "IT_ADMIN")
     * @return true if the user can see/access this product
     */
    public boolean isAccessibleByRole(String userRole) {
        if (userRole == null) {
            return false;
        }

        // SUPERADMIN can always see everything
        if ("SUPERADMIN".equalsIgnoreCase(userRole)) {
            return true;
        }

        // Check if role is explicitly excluded
        if (excludedRoles != null && !excludedRoles.isEmpty()) {
            String[] excluded = excludedRoles.toUpperCase().split(",");
            for (String role : excluded) {
                if (role.trim().equals(userRole.toUpperCase())) {
                    return false;
                }
            }
        }

        // If system app, only IT_ADMIN and SUPERADMIN can access
        if (Boolean.TRUE.equals(isSystemApp)) {
            return "IT_ADMIN".equalsIgnoreCase(userRole);
        }

        // If no allowed roles specified, accessible to all (except excluded)
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return true;
        }

        // Check if role is in allowed list
        String[] allowed = allowedRoles.toUpperCase().split(",");
        for (String role : allowed) {
            if (role.trim().equals(userRole.toUpperCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Set the allowed roles from a list.
     */
    public void setAllowedRolesList(String... roles) {
        this.allowedRoles = String.join(",", roles);
    }

    /**
     * Set the excluded roles from a list.
     */
    public void setExcludedRolesList(String... roles) {
        this.excludedRoles = String.join(",", roles);
    }
}
