package com.heronixedu.hub.service;

import com.heronixedu.hub.model.ThirdPartyApp;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.InstallerType;
import com.heronixedu.hub.model.enums.ThirdPartyAppCategory;
import com.heronixedu.hub.repository.ThirdPartyAppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing the third-party software catalog.
 * Handles adding, updating, approving, and removing applications from the catalog.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThirdPartyAppService {

    private final ThirdPartyAppRepository appRepository;
    private final AuditLogService auditLogService;

    /**
     * Get all applications in the catalog.
     */
    public List<ThirdPartyApp> getAllApps() {
        return appRepository.findAllByOrderByAppNameAsc();
    }

    /**
     * Get only approved applications.
     */
    public List<ThirdPartyApp> getApprovedApps() {
        return appRepository.findByIsApprovedTrueOrderByAppNameAsc();
    }

    /**
     * Get installed applications.
     */
    public List<ThirdPartyApp> getInstalledApps() {
        return appRepository.findByIsInstalledTrueOrderByAppNameAsc();
    }

    /**
     * Get applications by category.
     */
    public List<ThirdPartyApp> getAppsByCategory(ThirdPartyAppCategory category) {
        return appRepository.findByCategoryOrderByAppNameAsc(category);
    }

    /**
     * Get approved applications by category.
     */
    public List<ThirdPartyApp> getApprovedAppsByCategory(ThirdPartyAppCategory category) {
        return appRepository.findByIsApprovedTrueAndCategoryOrderByAppNameAsc(category);
    }

    /**
     * Search for applications by name, publisher, or tags.
     */
    public List<ThirdPartyApp> searchApps(String searchTerm, boolean approvedOnly) {
        if (approvedOnly) {
            return appRepository.searchApprovedApps(searchTerm);
        }
        return appRepository.searchAllApps(searchTerm);
    }

    /**
     * Get pending (unapproved) applications.
     */
    public List<ThirdPartyApp> getPendingApps() {
        return appRepository.findByIsApprovedFalseOrderByCreatedAtDesc();
    }

    /**
     * Find an application by its code.
     */
    public Optional<ThirdPartyApp> findByCode(String appCode) {
        return appRepository.findByAppCode(appCode);
    }

    /**
     * Find an application by ID.
     */
    public Optional<ThirdPartyApp> findById(Long id) {
        return appRepository.findById(id);
    }

    /**
     * Add a new application to the catalog.
     */
    @Transactional
    public ThirdPartyApp addApp(ThirdPartyApp app, User addedBy) {
        if (appRepository.existsByAppCode(app.getAppCode())) {
            throw new IllegalArgumentException("Application with code " + app.getAppCode() + " already exists");
        }

        app.setCreatedAt(LocalDateTime.now());
        app.setIsApproved(false);
        app.setIsInstalled(false);

        ThirdPartyApp saved = appRepository.save(app);
        auditLogService.logThirdPartyAppAdd(addedBy, saved,
                "Added " + app.getAppName() + " to software catalog");

        log.info("Added third-party app to catalog: {} by {}", app.getAppName(), addedBy.getUsername());
        return saved;
    }

    /**
     * Update an existing application.
     */
    @Transactional
    public ThirdPartyApp updateApp(ThirdPartyApp app, User updatedBy) {
        app.setLastUpdated(LocalDateTime.now());
        ThirdPartyApp saved = appRepository.save(app);

        auditLogService.log(
                com.heronixedu.hub.model.enums.AuditAction.THIRD_PARTY_APP_UPDATE,
                updatedBy.getUsername(),
                "Updated " + app.getAppName());

        log.info("Updated third-party app: {} by {}", app.getAppName(), updatedBy.getUsername());
        return saved;
    }

    /**
     * Approve an application for deployment.
     */
    @Transactional
    public ThirdPartyApp approveApp(Long appId, User approvedBy) {
        ThirdPartyApp app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));

        app.setIsApproved(true);
        app.setApprovedBy(approvedBy.getUsername());
        app.setApprovedAt(LocalDateTime.now());

        ThirdPartyApp saved = appRepository.save(app);
        auditLogService.logThirdPartyAppApprove(approvedBy, saved, true,
                "Approved " + app.getAppName() + " for deployment");

        log.info("Approved third-party app: {} by {}", app.getAppName(), approvedBy.getUsername());
        return saved;
    }

    /**
     * Revoke approval for an application.
     */
    @Transactional
    public ThirdPartyApp revokeApproval(Long appId, User revokedBy) {
        ThirdPartyApp app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));

        app.setIsApproved(false);
        app.setApprovedBy(null);
        app.setApprovedAt(null);

        ThirdPartyApp saved = appRepository.save(app);
        auditLogService.logThirdPartyAppApprove(revokedBy, saved, false,
                "Revoked approval for " + app.getAppName());

        log.info("Revoked approval for third-party app: {} by {}", app.getAppName(), revokedBy.getUsername());
        return saved;
    }

    /**
     * Remove an application from the catalog.
     */
    @Transactional
    public void removeApp(Long appId, User removedBy) {
        ThirdPartyApp app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));

        if (app.getIsInstalled()) {
            throw new IllegalStateException("Cannot remove installed application. Uninstall first.");
        }

        auditLogService.log(
                com.heronixedu.hub.model.enums.AuditAction.THIRD_PARTY_APP_REMOVE,
                removedBy.getUsername(),
                "Removed " + app.getAppName() + " from catalog");

        appRepository.delete(app);
        log.info("Removed third-party app from catalog: {} by {}", app.getAppName(), removedBy.getUsername());
    }

    /**
     * Get catalog statistics.
     */
    public CatalogStats getCatalogStats() {
        return new CatalogStats(
                appRepository.count(),
                appRepository.countApproved(),
                appRepository.countInstalled()
        );
    }

    /**
     * Create a new app builder with common defaults.
     */
    public static ThirdPartyApp.ThirdPartyAppBuilder createAppBuilder() {
        return ThirdPartyApp.builder()
                .isApproved(false)
                .isInstalled(false)
                .requiresAdmin(true)
                .requiresRestart(false)
                .supportedArchitectures("x64,x86")
                .createdAt(LocalDateTime.now());
    }

    /**
     * Quick method to create a common Windows app entry.
     */
    public ThirdPartyApp createWindowsApp(String appCode, String appName, String publisher,
                                           ThirdPartyAppCategory category, InstallerType installerType,
                                           String downloadUrl, String silentArgs) {
        return ThirdPartyApp.builder()
                .appCode(appCode)
                .appName(appName)
                .publisher(publisher)
                .category(category)
                .installerType(installerType)
                .downloadUrl(downloadUrl)
                .silentInstallArgs(silentArgs)
                .isApproved(false)
                .isInstalled(false)
                .requiresAdmin(true)
                .requiresRestart(false)
                .supportedArchitectures("x64,x86")
                .createdAt(LocalDateTime.now())
                .build();
    }

    public record CatalogStats(long total, long approved, long installed) {}
}
