package com.heronixedu.hub.service;

import com.heronixedu.hub.model.ThirdPartyApp;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.UpdatePolicy;
import com.heronixedu.hub.repository.ThirdPartyAppRepository;
import javafx.concurrent.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for managing application updates.
 * Handles update checking, approval workflow, and automatic updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppUpdateService {

    private final ThirdPartyAppRepository appRepository;
    private final ThirdPartyInstallerService installerService;
    private final AuditLogService auditLogService;
    private final NetworkConfigService networkConfigService;

    /**
     * Check all installed apps for updates based on their check interval.
     * This runs every hour and only checks apps that are due for an update check.
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void scheduledUpdateCheck() {
        log.info("Running scheduled update check...");
        List<ThirdPartyApp> appsToCheck = appRepository.findByIsInstalledTrueOrderByAppNameAsc()
                .stream()
                .filter(ThirdPartyApp::needsUpdateCheck)
                .toList();

        for (ThirdPartyApp app : appsToCheck) {
            try {
                checkForUpdate(app);
            } catch (Exception e) {
                log.error("Failed to check updates for {}: {}", app.getAppName(), e.getMessage());
            }
        }

        // Clean up expired approvals
        cleanupExpiredApprovals();

        // Process auto-updates
        processAutoUpdates();
    }

    /**
     * Revoke update approvals that have expired.
     */
    @Transactional
    public void cleanupExpiredApprovals() {
        List<ThirdPartyApp> appsWithApprovals = appRepository.findAll().stream()
                .filter(app -> app.getUpdateAvailable() && app.getUpdateApproved())
                .filter(ThirdPartyApp::isUpdateApprovalExpired)
                .toList();

        for (ThirdPartyApp app : appsWithApprovals) {
            log.info("Update approval expired for {}, revoking", app.getAppName());
            app.setUpdateApproved(false);
            app.setUpdateApprovedBy(null);
            app.setUpdateApprovedAt(null);
            app.setUpdateApprovalExpiresAt(null);
            appRepository.save(app);

            auditLogService.log(
                    com.heronixedu.hub.model.enums.AuditAction.THIRD_PARTY_APP_UPDATE,
                    "SYSTEM",
                    "Update approval expired for " + app.getAppName() + " version " + app.getPendingVersion()
            );
        }

        if (!appsWithApprovals.isEmpty()) {
            log.info("Revoked {} expired update approvals", appsWithApprovals.size());
        }
    }

    /**
     * Check for updates for a specific application.
     */
    @Transactional
    public UpdateCheckResult checkForUpdate(ThirdPartyApp app) {
        log.info("Checking for updates: {}", app.getAppName());

        try {
            app.setLastUpdateCheck(LocalDateTime.now());

            // If app has a specific update check URL, use it
            if (app.getUpdateCheckUrl() != null && !app.getUpdateCheckUrl().isEmpty()) {
                return checkUpdateFromUrl(app);
            }

            // Otherwise, try to check using common update patterns
            return checkUpdateFromKnownSources(app);

        } catch (Exception e) {
            log.error("Update check failed for {}: {}", app.getAppName(), e.getMessage());
            appRepository.save(app);
            return new UpdateCheckResult(false, null, e.getMessage());
        }
    }

    /**
     * Check update from a configured URL that returns version info.
     */
    private UpdateCheckResult checkUpdateFromUrl(ThirdPartyApp app) throws Exception {
        URI uri = URI.create(app.getUpdateCheckUrl());
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "Heronix-Hub/1.0");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            // Try to parse version from response (JSON or plain text)
            String responseText = response.toString();
            String newVersion = extractVersion(responseText);

            if (newVersion != null && !newVersion.equals(app.getCurrentVersion())) {
                // New version found
                String downloadUrl = extractDownloadUrl(responseText, app);
                app.setNewVersionAvailable(newVersion, downloadUrl, null, "Update available from publisher");
                appRepository.save(app);

                log.info("Update available for {}: {} -> {}", app.getAppName(), app.getCurrentVersion(), newVersion);
                return new UpdateCheckResult(true, newVersion, null);
            }

            appRepository.save(app);
            return new UpdateCheckResult(false, null, "Already at latest version");

        } finally {
            conn.disconnect();
        }
    }

    /**
     * Check updates from known software repositories.
     */
    private UpdateCheckResult checkUpdateFromKnownSources(ThirdPartyApp app) {
        // For now, just mark as checked - real implementation would integrate with
        // winget, chocolatey, or vendor-specific APIs
        appRepository.save(app);
        return new UpdateCheckResult(false, null, "No update source configured");
    }

    /**
     * Extract version number from response text.
     */
    private String extractVersion(String text) {
        // Try common JSON patterns
        Pattern jsonPattern = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
        Matcher jsonMatcher = jsonPattern.matcher(text);
        if (jsonMatcher.find()) {
            return jsonMatcher.group(1);
        }

        // Try semantic version pattern
        Pattern semverPattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+(?:\\.\\d+)?)");
        Matcher semverMatcher = semverPattern.matcher(text);
        if (semverMatcher.find()) {
            return semverMatcher.group(1);
        }

        return null;
    }

    /**
     * Extract download URL from response text.
     */
    private String extractDownloadUrl(String text, ThirdPartyApp app) {
        // Try common JSON patterns
        Pattern urlPattern = Pattern.compile("\"(?:download_url|downloadUrl|url)\"\\s*:\\s*\"([^\"]+)\"");
        Matcher urlMatcher = urlPattern.matcher(text);
        if (urlMatcher.find()) {
            return urlMatcher.group(1);
        }

        // Fall back to existing download URL
        return app.getDownloadUrl();
    }

    /**
     * Process applications set to auto-update.
     */
    @Transactional
    public void processAutoUpdates() {
        List<ThirdPartyApp> autoUpdateApps = appRepository.findAll().stream()
                .filter(app -> app.getUpdatePolicy() == UpdatePolicy.AUTO)
                .filter(app -> app.getUpdateAvailable() && app.isUpdateReadyToInstall())
                .filter(app -> app.getUpdateFailedCount() < 3) // Don't retry too many times
                .toList();

        for (ThirdPartyApp app : autoUpdateApps) {
            log.info("Auto-updating: {} to version {}", app.getAppName(), app.getPendingVersion());
            try {
                performUpdate(app, null);
            } catch (Exception e) {
                log.error("Auto-update failed for {}: {}", app.getAppName(), e.getMessage());
                app.markUpdateFailed(e.getMessage());
                appRepository.save(app);
            }
        }
    }

    /**
     * Approve a pending update for installation.
     */
    @Transactional
    public ThirdPartyApp approveUpdate(Long appId, User approvedBy) {
        ThirdPartyApp app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (!app.getUpdateAvailable()) {
            throw new IllegalStateException("No update available for this application");
        }

        app.approveUpdate(approvedBy.getUsername());
        ThirdPartyApp saved = appRepository.save(app);

        auditLogService.log(
                com.heronixedu.hub.model.enums.AuditAction.THIRD_PARTY_APP_UPDATE,
                approvedBy.getUsername(),
                "Approved update for " + app.getAppName() + " to version " + app.getPendingVersion()
        );

        log.info("Update approved for {} by {}", app.getAppName(), approvedBy.getUsername());
        return saved;
    }

    /**
     * Reject/dismiss a pending update.
     */
    @Transactional
    public ThirdPartyApp rejectUpdate(Long appId, User rejectedBy) {
        ThirdPartyApp app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        String pendingVersion = app.getPendingVersion();
        app.clearPendingUpdate();
        ThirdPartyApp saved = appRepository.save(app);

        auditLogService.log(
                com.heronixedu.hub.model.enums.AuditAction.THIRD_PARTY_APP_UPDATE,
                rejectedBy.getUsername(),
                "Rejected update for " + app.getAppName() + " version " + pendingVersion
        );

        log.info("Update rejected for {} by {}", app.getAppName(), rejectedBy.getUsername());
        return saved;
    }

    /**
     * Perform the actual update installation.
     */
    public Task<UpdateResult> performUpdate(ThirdPartyApp app, Consumer<Double> progressCallback) {
        return new Task<>() {
            @Override
            protected UpdateResult call() throws Exception {
                log.info("Starting update for {} to version {}", app.getAppName(), app.getPendingVersion());

                try {
                    if (progressCallback != null) {
                        progressCallback.accept(0.0);
                    }

                    // Use the pending version's download URL
                    String originalUrl = app.getDownloadUrl();
                    String originalChecksum = app.getChecksumSha256();

                    // Temporarily set the pending version's details for installation
                    if (app.getPendingDownloadUrl() != null) {
                        app.setDownloadUrl(app.getPendingDownloadUrl());
                    }
                    if (app.getPendingChecksum() != null) {
                        app.setChecksumSha256(app.getPendingChecksum());
                    }

                    // Perform installation using existing installer service
                    Task<ThirdPartyInstallerService.InstallationResult> installTask =
                            installerService.installApp(app, null, progress -> {
                                if (progressCallback != null) {
                                    progressCallback.accept(progress);
                                }
                            });

                    // Run synchronously
                    installTask.run();
                    ThirdPartyInstallerService.InstallationResult installResult = installTask.get();

                    if (installResult.isSuccess()) {
                        // Mark update as complete with rollback support
                        app.completeUpdateWithRollback();
                        appRepository.save(app);

                        auditLogService.log(
                                com.heronixedu.hub.model.enums.AuditAction.THIRD_PARTY_APP_UPDATE,
                                "SYSTEM",
                                "Update installed successfully: " + app.getAppName() +
                                        " updated to v" + app.getCurrentVersion() +
                                        " (rollback available to v" + app.getPreviousVersion() + ")"
                        );

                        log.info("Update completed successfully for {}", app.getAppName());
                        return new UpdateResult(true, app.getCurrentVersion(), null);
                    } else {
                        // Restore original URLs on failure
                        app.setDownloadUrl(originalUrl);
                        app.setChecksumSha256(originalChecksum);
                        app.markUpdateFailed(installResult.getErrorMessage());
                        appRepository.save(app);

                        return new UpdateResult(false, null, installResult.getErrorMessage());
                    }

                } catch (Exception e) {
                    log.error("Update failed for {}: {}", app.getAppName(), e.getMessage());
                    app.markUpdateFailed(e.getMessage());
                    appRepository.save(app);
                    throw e;
                }
            }
        };
    }

    /**
     * Set the update policy for an application.
     */
    @Transactional
    public ThirdPartyApp setUpdatePolicy(Long appId, UpdatePolicy policy, User changedBy) {
        ThirdPartyApp app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        UpdatePolicy oldPolicy = app.getUpdatePolicy();
        app.setUpdatePolicy(policy);

        // If changing to AUTO, also set autoUpdateEnabled
        app.setAutoUpdateEnabled(policy == UpdatePolicy.AUTO);

        ThirdPartyApp saved = appRepository.save(app);

        auditLogService.log(
                com.heronixedu.hub.model.enums.AuditAction.THIRD_PARTY_APP_UPDATE,
                changedBy.getUsername(),
                "Changed update policy for " + app.getAppName() + " from " + oldPolicy + " to " + policy
        );

        log.info("Update policy changed for {} to {} by {}", app.getAppName(), policy, changedBy.getUsername());
        return saved;
    }

    /**
     * Set the update check interval for an application.
     */
    @Transactional
    public ThirdPartyApp setUpdateCheckInterval(Long appId, int hours, User changedBy) {
        ThirdPartyApp app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        app.setUpdateCheckIntervalHours(hours);
        ThirdPartyApp saved = appRepository.save(app);

        log.info("Update check interval changed for {} to {} hours by {}",
                app.getAppName(), hours, changedBy.getUsername());
        return saved;
    }

    /**
     * Get all apps with pending updates.
     */
    public List<ThirdPartyApp> getAppsWithPendingUpdates() {
        return appRepository.findAll().stream()
                .filter(app -> app.getUpdateAvailable() && !app.getUpdateApproved())
                .toList();
    }

    /**
     * Get all apps with approved updates ready to install.
     */
    public List<ThirdPartyApp> getAppsWithApprovedUpdates() {
        return appRepository.findAll().stream()
                .filter(ThirdPartyApp::isUpdateReadyToInstall)
                .toList();
    }

    /**
     * Force an immediate update check for all apps.
     */
    public CompletableFuture<List<UpdateCheckResult>> checkAllForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            List<ThirdPartyApp> installedApps = appRepository.findByIsInstalledTrueOrderByAppNameAsc();
            return installedApps.stream()
                    .map(this::checkForUpdate)
                    .toList();
        });
    }

    /**
     * Rollback an application to its previous version.
     */
    @Transactional
    public ThirdPartyApp rollbackUpdate(Long appId, User rolledBackBy) {
        ThirdPartyApp app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (!Boolean.TRUE.equals(app.getRollbackAvailable())) {
            throw new IllegalStateException("Rollback not available for this application");
        }

        String currentVersion = app.getCurrentVersion();
        String previousVersion = app.getPreviousVersion();

        app.rollback();
        ThirdPartyApp saved = appRepository.save(app);

        auditLogService.log(
                com.heronixedu.hub.model.enums.AuditAction.UPDATE_ROLLBACK,
                rolledBackBy.getUsername(),
                "Rolled back " + app.getAppName() + " from " + currentVersion + " to " + previousVersion
        );

        log.info("Rollback completed for {} by {}: {} -> {}",
                app.getAppName(), rolledBackBy.getUsername(), currentVersion, previousVersion);
        return saved;
    }

    /**
     * Clear rollback data for an application (confirm current version is good).
     */
    @Transactional
    public ThirdPartyApp confirmCurrentVersion(Long appId, User confirmedBy) {
        ThirdPartyApp app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        app.clearRollback();
        ThirdPartyApp saved = appRepository.save(app);

        auditLogService.log(
                com.heronixedu.hub.model.enums.AuditAction.THIRD_PARTY_APP_UPDATE,
                confirmedBy.getUsername(),
                "Confirmed current version of " + app.getAppName() + " v" + app.getCurrentVersion() + ", rollback data cleared"
        );

        log.info("Current version confirmed for {}, rollback cleared", app.getAppName());
        return saved;
    }

    /**
     * Get all apps with rollback available.
     */
    public List<ThirdPartyApp> getAppsWithRollbackAvailable() {
        return appRepository.findAll().stream()
                .filter(app -> Boolean.TRUE.equals(app.getRollbackAvailable()))
                .toList();
    }

    /**
     * Set the approval expiration time for an application.
     */
    @Transactional
    public ThirdPartyApp setApprovalExpiration(Long appId, int hours, User changedBy) {
        ThirdPartyApp app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        app.setApprovalExpirationHours(hours);
        ThirdPartyApp saved = appRepository.save(app);

        auditLogService.log(
                com.heronixedu.hub.model.enums.AuditAction.THIRD_PARTY_APP_UPDATE,
                changedBy.getUsername(),
                "Set approval expiration for " + app.getAppName() + " to " + hours + " hours"
        );

        log.info("Approval expiration set for {} to {} hours by {}",
                app.getAppName(), hours, changedBy.getUsername());
        return saved;
    }

    /**
     * Result of an update check.
     */
    public record UpdateCheckResult(boolean updateFound, String newVersion, String message) {}

    /**
     * Result of an update installation.
     */
    public record UpdateResult(boolean success, String newVersion, String errorMessage) {}
}
