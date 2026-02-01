package com.heronixedu.hub.service;

import com.heronixedu.hub.model.AppAccessPolicy;
import com.heronixedu.hub.model.AppAccessPolicy.PolicyTargetType;
import com.heronixedu.hub.model.Product;
import com.heronixedu.hub.model.ThirdPartyApp;
import com.heronixedu.hub.model.enums.AuditAction;
import com.heronixedu.hub.repository.AppAccessPolicyRepository;
import com.heronixedu.hub.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing application access policies.
 * Controls which roles can access which Heronix products and third-party apps.
 *
 * Access logic:
 * - SUPERADMIN always has access to everything (hardcoded, cannot be revoked)
 * - For other roles, access is determined by policies in the database
 * - If no policy exists for a role+target, access is DENIED by default
 * - Administrators can grant access per role to products, categories, or individual apps
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppAccessPolicyService {

    private final AppAccessPolicyRepository policyRepository;
    private final ProductRepository productRepository;
    private final AuditLogService auditLogService;

    // ============== ACCESS CHECKS ==============

    /**
     * Check if a role can access a specific Heronix product.
     */
    public boolean canAccessProduct(String roleName, String productCode) {
        // SUPERADMIN always has access
        if ("SUPERADMIN".equalsIgnoreCase(roleName)) {
            return true;
        }

        return policyRepository.findByRoleNameAndTargetTypeAndTargetCode(
                roleName.toUpperCase(), PolicyTargetType.HERONIX_PRODUCT, productCode.toUpperCase()
        ).map(AppAccessPolicy::getAccessGranted).orElse(false);
    }

    /**
     * Check if a role can access a third-party app category.
     */
    public boolean canAccessCategory(String roleName, String categoryName) {
        if ("SUPERADMIN".equalsIgnoreCase(roleName)) {
            return true;
        }

        return policyRepository.findByRoleNameAndTargetTypeAndTargetCode(
                roleName.toUpperCase(), PolicyTargetType.THIRDPARTY_CATEGORY, categoryName.toUpperCase()
        ).map(AppAccessPolicy::getAccessGranted).orElse(false);
    }

    /**
     * Check if a role can access a specific third-party app.
     * Checks app-specific policy first, then falls back to category policy.
     */
    public boolean canAccessThirdPartyApp(String roleName, ThirdPartyApp app) {
        if ("SUPERADMIN".equalsIgnoreCase(roleName)) {
            return true;
        }

        // Check app-specific policy first
        Optional<AppAccessPolicy> appPolicy = policyRepository.findByRoleNameAndTargetTypeAndTargetCode(
                roleName.toUpperCase(), PolicyTargetType.THIRDPARTY_APP, app.getAppCode().toUpperCase()
        );
        if (appPolicy.isPresent()) {
            return appPolicy.get().getAccessGranted();
        }

        // Fall back to category policy
        return canAccessCategory(roleName, app.getCategory().name());
    }

    /**
     * Get all Heronix products accessible by a role.
     */
    public List<Product> getAccessibleProducts(String roleName) {
        if ("SUPERADMIN".equalsIgnoreCase(roleName)) {
            return productRepository.findAllByIsInstalledTrue();
        }

        Set<String> grantedCodes = policyRepository.findGrantedTargetCodes(
                roleName.toUpperCase(), PolicyTargetType.HERONIX_PRODUCT
        ).stream().map(String::toUpperCase).collect(Collectors.toSet());

        return productRepository.findAllByIsInstalledTrue().stream()
                .filter(p -> grantedCodes.contains(p.getProductCode().toUpperCase()))
                .toList();
    }

    /**
     * Get all third-party app categories accessible by a role.
     */
    public Set<String> getAccessibleCategories(String roleName) {
        if ("SUPERADMIN".equalsIgnoreCase(roleName)) {
            return Set.of(); // Empty means all categories (handled by caller)
        }

        return policyRepository.findGrantedTargetCodes(
                roleName.toUpperCase(), PolicyTargetType.THIRDPARTY_CATEGORY
        ).stream().map(String::toUpperCase).collect(Collectors.toSet());
    }

    /**
     * Filter a list of third-party apps based on role access policies.
     */
    public List<ThirdPartyApp> filterAccessibleApps(String roleName, List<ThirdPartyApp> apps) {
        if ("SUPERADMIN".equalsIgnoreCase(roleName)) {
            return apps;
        }

        // Get category-level and app-level grants
        Set<String> grantedCategories = getAccessibleCategories(roleName);
        Set<String> grantedApps = policyRepository.findGrantedTargetCodes(
                roleName.toUpperCase(), PolicyTargetType.THIRDPARTY_APP
        ).stream().map(String::toUpperCase).collect(Collectors.toSet());

        // Also get explicitly denied apps
        List<AppAccessPolicy> appDenials = policyRepository.findByRoleNameAndTargetType(
                roleName.toUpperCase(), PolicyTargetType.THIRDPARTY_APP
        );
        Set<String> deniedApps = appDenials.stream()
                .filter(p -> !p.getAccessGranted())
                .map(p -> p.getTargetCode().toUpperCase())
                .collect(Collectors.toSet());

        return apps.stream().filter(app -> {
            String appCode = app.getAppCode().toUpperCase();

            // If explicitly denied at app level, deny
            if (deniedApps.contains(appCode)) {
                return false;
            }

            // If explicitly granted at app level, allow
            if (grantedApps.contains(appCode)) {
                return true;
            }

            // Fall back to category-level grant
            return grantedCategories.contains(app.getCategory().name().toUpperCase());
        }).toList();
    }

    // ============== POLICY MANAGEMENT ==============

    /**
     * Grant access for a role to a Heronix product.
     */
    @Transactional
    public AppAccessPolicy grantProductAccess(String roleName, String productCode, String modifiedBy) {
        return setPolicy(roleName, PolicyTargetType.HERONIX_PRODUCT, productCode, true, modifiedBy, null);
    }

    /**
     * Revoke access for a role from a Heronix product.
     */
    @Transactional
    public AppAccessPolicy revokeProductAccess(String roleName, String productCode, String modifiedBy) {
        return setPolicy(roleName, PolicyTargetType.HERONIX_PRODUCT, productCode, false, modifiedBy, null);
    }

    /**
     * Grant access for a role to a third-party app category.
     */
    @Transactional
    public AppAccessPolicy grantCategoryAccess(String roleName, String category, String modifiedBy) {
        return setPolicy(roleName, PolicyTargetType.THIRDPARTY_CATEGORY, category, true, modifiedBy, null);
    }

    /**
     * Revoke access for a role from a third-party app category.
     */
    @Transactional
    public AppAccessPolicy revokeCategoryAccess(String roleName, String category, String modifiedBy) {
        return setPolicy(roleName, PolicyTargetType.THIRDPARTY_CATEGORY, category, false, modifiedBy, null);
    }

    /**
     * Grant access for a role to a specific third-party app.
     */
    @Transactional
    public AppAccessPolicy grantAppAccess(String roleName, String appCode, String modifiedBy) {
        return setPolicy(roleName, PolicyTargetType.THIRDPARTY_APP, appCode, true, modifiedBy, null);
    }

    /**
     * Revoke access for a role from a specific third-party app.
     */
    @Transactional
    public AppAccessPolicy revokeAppAccess(String roleName, String appCode, String modifiedBy) {
        return setPolicy(roleName, PolicyTargetType.THIRDPARTY_APP, appCode, false, modifiedBy, null);
    }

    /**
     * Set or update a policy.
     */
    @Transactional
    public AppAccessPolicy setPolicy(String roleName, PolicyTargetType targetType, String targetCode,
                                      boolean granted, String modifiedBy, String note) {
        String upperRole = roleName.toUpperCase();
        String upperCode = targetCode.toUpperCase();

        Optional<AppAccessPolicy> existing = policyRepository.findByRoleNameAndTargetTypeAndTargetCode(
                upperRole, targetType, upperCode
        );

        AppAccessPolicy policy;
        if (existing.isPresent()) {
            policy = existing.get();
            policy.setAccessGranted(granted);
            policy.setModifiedBy(modifiedBy);
            if (note != null) {
                policy.setPolicyNote(note);
            }
        } else {
            policy = AppAccessPolicy.builder()
                    .roleName(upperRole)
                    .targetType(targetType)
                    .targetCode(upperCode)
                    .accessGranted(granted)
                    .modifiedBy(modifiedBy)
                    .policyNote(note)
                    .build();
        }

        AppAccessPolicy saved = policyRepository.save(policy);

        auditLogService.log(
                AuditAction.CONFIG_CHANGE,
                modifiedBy != null ? modifiedBy : "system",
                String.format("Access policy: %s %s for role %s to %s:%s",
                        granted ? "GRANTED" : "REVOKED", targetType, upperRole, targetType, upperCode)
        );

        log.info("Policy set: {} {} for role {} -> {}:{}",
                granted ? "GRANTED" : "REVOKED", targetType, upperRole, targetType, upperCode);

        return saved;
    }

    /**
     * Get all policies for a role.
     */
    public List<AppAccessPolicy> getPoliciesForRole(String roleName) {
        return policyRepository.findByRoleName(roleName.toUpperCase());
    }

    /**
     * Get all policies (for admin panel).
     */
    public List<AppAccessPolicy> getAllPolicies() {
        return policyRepository.findAll();
    }

    /**
     * Delete a specific policy (resets to default deny).
     */
    @Transactional
    public void deletePolicy(Long policyId) {
        policyRepository.deleteById(policyId);
    }

    /**
     * Check if any policies have been initialized.
     */
    public boolean hasPolicies() {
        return policyRepository.count() > 0;
    }
}
