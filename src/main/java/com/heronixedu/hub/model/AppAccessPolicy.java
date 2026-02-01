package com.heronixedu.hub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Configurable access policy that controls which roles can access which applications.
 * Administrators can grant or revoke access per role to both Heronix products
 * and third-party application categories.
 *
 * Policy types:
 * - HERONIX_PRODUCT: Controls access to a specific Heronix product (e.g., "TEACHER", "SIS")
 * - THIRDPARTY_CATEGORY: Controls access to a third-party app category (e.g., "BROWSER", "EDUCATION")
 * - THIRDPARTY_APP: Controls access to a specific third-party app (e.g., "CHROME", "ZOOM")
 */
@Entity
@Table(name = "app_access_policies",
        uniqueConstraints = @UniqueConstraint(columnNames = {"role_name", "target_type", "target_code"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppAccessPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The role this policy applies to (e.g., "TEACHER", "STUDENT", "IT_ADMIN", "SUPERADMIN")
    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;

    // Type of target this policy controls access to
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private PolicyTargetType targetType;

    // The code identifying the target (product code, category name, or app code)
    @Column(name = "target_code", nullable = false, length = 100)
    private String targetCode;

    // Whether access is granted
    @Column(name = "access_granted", nullable = false)
    private Boolean accessGranted;

    // Who created/modified this policy
    @Column(name = "modified_by", length = 100)
    private String modifiedBy;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    // Optional note explaining why this policy was set
    @Column(name = "policy_note", length = 500)
    private String policyNote;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        modifiedAt = LocalDateTime.now();
    }

    public enum PolicyTargetType {
        HERONIX_PRODUCT,        // Specific Heronix product (target_code = product code like "SIS")
        THIRDPARTY_CATEGORY,    // Third-party app category (target_code = category like "BROWSER")
        THIRDPARTY_APP          // Specific third-party app (target_code = app code like "CHROME")
    }
}
