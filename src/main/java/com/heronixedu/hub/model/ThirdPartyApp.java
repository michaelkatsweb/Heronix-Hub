package com.heronixedu.hub.model;

import com.heronixedu.hub.model.enums.ThirdPartyAppCategory;
import com.heronixedu.hub.model.enums.InstallerType;
import com.heronixedu.hub.model.enums.UpdatePolicy;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a third-party application that can be deployed via Heronix Hub.
 * IT administrators can add approved applications to this catalog for deployment
 * across all managed computers.
 */
@Entity
@Table(name = "third_party_apps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_code", unique = true, nullable = false, length = 50)
    private String appCode;

    @Column(name = "app_name", nullable = false, length = 100)
    private String appName;

    @Column(name = "publisher", length = 100)
    private String publisher;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private ThirdPartyAppCategory category;

    @Column(name = "current_version", length = 50)
    private String currentVersion;

    @Column(name = "latest_version", length = 50)
    private String latestVersion;

    @Column(name = "download_url", length = 1000)
    private String downloadUrl;

    @Column(name = "local_path", length = 500)
    private String localPath;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "installer_type", nullable = false)
    private InstallerType installerType;

    @Column(name = "silent_install_args", length = 500)
    private String silentInstallArgs;

    @Column(name = "uninstall_command", length = 500)
    private String uninstallCommand;

    @Column(name = "install_path", length = 500)
    private String installPath;

    @Column(name = "executable_name", length = 255)
    private String executableName;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "is_approved")
    @Builder.Default
    private Boolean isApproved = false;

    @Column(name = "is_installed")
    @Builder.Default
    private Boolean isInstalled = false;

    @Column(name = "requires_admin")
    @Builder.Default
    private Boolean requiresAdmin = true;

    @Column(name = "requires_restart")
    @Builder.Default
    private Boolean requiresRestart = false;

    @Column(name = "license_type", length = 50)
    private String licenseType;

    @Column(name = "license_key", length = 255)
    private String licenseKey;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "installed_at")
    private LocalDateTime installedAt;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "min_os_version", length = 50)
    private String minOsVersion;

    @Column(name = "supported_architectures", length = 50)
    @Builder.Default
    private String supportedArchitectures = "x64,x86";

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "documentation_url", length = 500)
    private String documentationUrl;

    @Column(name = "tags", length = 500)
    private String tags;

    // ========== Update Management Fields ==========

    @Enumerated(EnumType.STRING)
    @Column(name = "update_policy")
    @Builder.Default
    private UpdatePolicy updatePolicy = UpdatePolicy.ADMIN_APPROVED;

    @Column(name = "update_check_url", length = 1000)
    private String updateCheckUrl;

    @Column(name = "pending_version", length = 50)
    private String pendingVersion;

    @Column(name = "pending_download_url", length = 1000)
    private String pendingDownloadUrl;

    @Column(name = "pending_checksum", length = 64)
    private String pendingChecksum;

    @Column(name = "update_available")
    @Builder.Default
    private Boolean updateAvailable = false;

    @Column(name = "update_approved")
    @Builder.Default
    private Boolean updateApproved = false;

    @Column(name = "update_approved_by", length = 100)
    private String updateApprovedBy;

    @Column(name = "update_approved_at")
    private LocalDateTime updateApprovedAt;

    /**
     * Expiration time for update approval. After this time, approval is revoked.
     */
    @Column(name = "update_approval_expires_at")
    private LocalDateTime updateApprovalExpiresAt;

    /**
     * Default approval expiration in hours (0 = never expires).
     */
    @Column(name = "approval_expiration_hours")
    @Builder.Default
    private Integer approvalExpirationHours = 72;

    @Column(name = "last_update_check")
    private LocalDateTime lastUpdateCheck;

    @Column(name = "update_check_interval_hours")
    @Builder.Default
    private Integer updateCheckIntervalHours = 24;

    @Column(name = "auto_update_enabled")
    @Builder.Default
    private Boolean autoUpdateEnabled = false;

    @Column(name = "update_notes", columnDefinition = "TEXT")
    private String updateNotes;

    @Column(name = "update_failed_count")
    @Builder.Default
    private Integer updateFailedCount = 0;

    @Column(name = "last_update_error", length = 1000)
    private String lastUpdateError;

    // ========== Security Fields ==========

    /**
     * Whether digital signature verification is required for this app.
     */
    @Column(name = "require_signature")
    @Builder.Default
    private Boolean requireSignature = true;

    /**
     * Expected publisher name for signature verification.
     */
    @Column(name = "expected_publisher", length = 255)
    private String expectedPublisher;

    /**
     * Expected certificate thumbprint for pinning.
     */
    @Column(name = "expected_cert_thumbprint", length = 64)
    private String expectedCertThumbprint;

    /**
     * Last verified signer name from digital signature.
     */
    @Column(name = "last_verified_signer", length = 255)
    private String lastVerifiedSigner;

    /**
     * Timestamp of last successful signature verification.
     */
    @Column(name = "last_signature_check")
    private LocalDateTime lastSignatureCheck;

    // ========== Rollback Fields ==========

    /**
     * Previous version before last update (for rollback).
     */
    @Column(name = "previous_version", length = 50)
    private String previousVersion;

    /**
     * Previous download URL (for rollback).
     */
    @Column(name = "previous_download_url", length = 1000)
    private String previousDownloadUrl;

    /**
     * Previous checksum (for rollback).
     */
    @Column(name = "previous_checksum", length = 64)
    private String previousChecksum;

    /**
     * Whether rollback is available for this app.
     */
    @Column(name = "rollback_available")
    @Builder.Default
    private Boolean rollbackAvailable = false;

    public boolean hasUpdate() {
        if (latestVersion == null || currentVersion == null) {
            return false;
        }
        return !latestVersion.equals(currentVersion);
    }

    public boolean isReadyForInstall() {
        return isApproved && (downloadUrl != null || localPath != null);
    }

    /**
     * Check if an update is ready to be installed based on the update policy.
     */
    public boolean isUpdateReadyToInstall() {
        if (!updateAvailable || pendingVersion == null) {
            return false;
        }

        // Check if approval has expired
        if (isUpdateApprovalExpired()) {
            return false;
        }

        return switch (updatePolicy) {
            case AUTO -> true;
            case ADMIN_APPROVED -> updateApproved;
            case MANUAL -> updateApproved;
            case DISABLED -> false;
        };
    }

    /**
     * Check if this app needs an update check based on the interval.
     */
    public boolean needsUpdateCheck() {
        if (updatePolicy == UpdatePolicy.DISABLED) {
            return false;
        }
        if (lastUpdateCheck == null) {
            return true;
        }
        return lastUpdateCheck.plusHours(updateCheckIntervalHours).isBefore(LocalDateTime.now());
    }

    /**
     * Mark that a new version is available and pending approval/installation.
     */
    public void setNewVersionAvailable(String version, String downloadUrl, String checksum, String notes) {
        this.pendingVersion = version;
        this.pendingDownloadUrl = downloadUrl;
        this.pendingChecksum = checksum;
        this.updateNotes = notes;
        this.updateAvailable = true;
        this.updateApproved = (updatePolicy == UpdatePolicy.AUTO);
        this.lastUpdateCheck = LocalDateTime.now();
    }

    /**
     * Approve the pending update for installation.
     */
    public void approveUpdate(String approvedBy) {
        this.updateApproved = true;
        this.updateApprovedBy = approvedBy;
        this.updateApprovedAt = LocalDateTime.now();

        // Set expiration if configured
        if (approvalExpirationHours != null && approvalExpirationHours > 0) {
            this.updateApprovalExpiresAt = LocalDateTime.now().plusHours(approvalExpirationHours);
        } else {
            this.updateApprovalExpiresAt = null;
        }
    }

    /**
     * Check if the update approval has expired.
     */
    public boolean isUpdateApprovalExpired() {
        if (!updateApproved || updateApprovalExpiresAt == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(updateApprovalExpiresAt);
    }

    /**
     * Mark the update as installed successfully.
     */
    public void completeUpdate() {
        this.currentVersion = this.pendingVersion;
        this.downloadUrl = this.pendingDownloadUrl;
        this.checksumSha256 = this.pendingChecksum;
        this.pendingVersion = null;
        this.pendingDownloadUrl = null;
        this.pendingChecksum = null;
        this.updateAvailable = false;
        this.updateApproved = false;
        this.updateApprovedBy = null;
        this.updateApprovedAt = null;
        this.updateNotes = null;
        this.updateFailedCount = 0;
        this.lastUpdateError = null;
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Mark an update attempt as failed.
     */
    public void markUpdateFailed(String error) {
        this.updateFailedCount++;
        this.lastUpdateError = error;
    }

    /**
     * Clear the pending update (reject or cancel).
     */
    public void clearPendingUpdate() {
        this.pendingVersion = null;
        this.pendingDownloadUrl = null;
        this.pendingChecksum = null;
        this.updateAvailable = false;
        this.updateApproved = false;
        this.updateApprovedBy = null;
        this.updateApprovedAt = null;
        this.updateNotes = null;
    }

    /**
     * Prepare for update by saving current version info for potential rollback.
     */
    public void prepareForUpdate() {
        this.previousVersion = this.currentVersion;
        this.previousDownloadUrl = this.downloadUrl;
        this.previousChecksum = this.checksumSha256;
    }

    /**
     * Mark the update as complete and enable rollback.
     */
    public void completeUpdateWithRollback() {
        prepareForUpdate();
        completeUpdate();
        this.rollbackAvailable = true;
    }

    /**
     * Rollback to the previous version.
     */
    public void rollback() {
        if (!rollbackAvailable || previousVersion == null) {
            throw new IllegalStateException("Rollback not available for this application");
        }

        // Swap current and previous
        String tempVersion = this.currentVersion;
        String tempUrl = this.downloadUrl;
        String tempChecksum = this.checksumSha256;

        this.currentVersion = this.previousVersion;
        this.downloadUrl = this.previousDownloadUrl;
        this.checksumSha256 = this.previousChecksum;

        this.previousVersion = tempVersion;
        this.previousDownloadUrl = tempUrl;
        this.previousChecksum = tempChecksum;

        this.lastUpdated = LocalDateTime.now();
        // Keep rollback available so user can "undo" the rollback
    }

    /**
     * Clear rollback data (after confirming current version works).
     */
    public void clearRollback() {
        this.previousVersion = null;
        this.previousDownloadUrl = null;
        this.previousChecksum = null;
        this.rollbackAvailable = false;
    }

    /**
     * Record successful signature verification.
     */
    public void recordSignatureVerification(String signerName) {
        this.lastVerifiedSigner = signerName;
        this.lastSignatureCheck = LocalDateTime.now();
    }
}
