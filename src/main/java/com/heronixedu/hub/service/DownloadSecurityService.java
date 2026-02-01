package com.heronixedu.hub.service;

import com.heronixedu.hub.model.DownloadSourcePolicy;
import com.heronixedu.hub.model.ThirdPartyApp;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.AuditAction;
import com.heronixedu.hub.repository.DownloadSourcePolicyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing download source security policies.
 * Provides whitelist/blacklist functionality for download URLs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadSecurityService {

    private final DownloadSourcePolicyRepository policyRepository;
    private final AuditLogService auditLogService;

    /**
     * Result of a download URL security check.
     */
    public record SecurityCheckResult(
            boolean isAllowed,
            String reason,
            DownloadSourcePolicy matchedPolicy
    ) {
        public static SecurityCheckResult allowed(String reason) {
            return new SecurityCheckResult(true, reason, null);
        }

        public static SecurityCheckResult allowedByPolicy(DownloadSourcePolicy policy) {
            return new SecurityCheckResult(true, "Allowed by policy: " + policy.getDescription(), policy);
        }

        public static SecurityCheckResult denied(String reason) {
            return new SecurityCheckResult(false, reason, null);
        }

        public static SecurityCheckResult deniedByPolicy(DownloadSourcePolicy policy) {
            return new SecurityCheckResult(false, "Blocked by policy: " + policy.getDescription(), policy);
        }
    }

    /**
     * Initialize default security policies if none exist.
     */
    @PostConstruct
    @Transactional
    public void initializeDefaultPolicies() {
        if (policyRepository.count() > 0) {
            return;
        }

        log.info("Initializing default download source security policies...");

        // Default whitelist - trusted software sources
        List<DownloadSourcePolicy> defaultPolicies = List.of(
                // Major software vendors
                DownloadSourcePolicy.builder()
                        .pattern("microsoft.com")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(10)
                        .description("Microsoft official downloads")
                        .createdBy("SYSTEM")
                        .build(),
                DownloadSourcePolicy.builder()
                        .pattern("google.com")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(10)
                        .description("Google official downloads")
                        .createdBy("SYSTEM")
                        .build(),
                DownloadSourcePolicy.builder()
                        .pattern("mozilla.org")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(10)
                        .description("Mozilla official downloads")
                        .createdBy("SYSTEM")
                        .build(),
                DownloadSourcePolicy.builder()
                        .pattern("adobe.com")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(10)
                        .description("Adobe official downloads")
                        .createdBy("SYSTEM")
                        .build(),

                // Developer/Educational tools
                DownloadSourcePolicy.builder()
                        .pattern("github.com")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(20)
                        .description("GitHub releases")
                        .createdBy("SYSTEM")
                        .build(),
                DownloadSourcePolicy.builder()
                        .pattern("githubusercontent.com")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(20)
                        .description("GitHub raw content")
                        .createdBy("SYSTEM")
                        .build(),
                DownloadSourcePolicy.builder()
                        .pattern("sourceforge.net")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(30)
                        .description("SourceForge downloads")
                        .createdBy("SYSTEM")
                        .build(),

                // Educational software
                DownloadSourcePolicy.builder()
                        .pattern("geogebra.org")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(20)
                        .description("GeoGebra educational software")
                        .createdBy("SYSTEM")
                        .build(),
                DownloadSourcePolicy.builder()
                        .pattern("scratch.mit.edu")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(20)
                        .description("MIT Scratch programming")
                        .createdBy("SYSTEM")
                        .build(),
                DownloadSourcePolicy.builder()
                        .pattern("python.org")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(20)
                        .description("Python official downloads")
                        .createdBy("SYSTEM")
                        .build(),

                // Communication tools
                DownloadSourcePolicy.builder()
                        .pattern("zoom.us")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(20)
                        .description("Zoom video conferencing")
                        .createdBy("SYSTEM")
                        .build(),

                // Productivity
                DownloadSourcePolicy.builder()
                        .pattern("libreoffice.org")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(20)
                        .description("LibreOffice suite")
                        .createdBy("SYSTEM")
                        .build(),
                DownloadSourcePolicy.builder()
                        .pattern("videolan.org")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(20)
                        .description("VLC media player")
                        .createdBy("SYSTEM")
                        .build(),
                DownloadSourcePolicy.builder()
                        .pattern("7-zip.org")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(20)
                        .description("7-Zip archiver")
                        .createdBy("SYSTEM")
                        .build(),
                DownloadSourcePolicy.builder()
                        .pattern("notepad-plus-plus.org")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(20)
                        .description("Notepad++ text editor")
                        .createdBy("SYSTEM")
                        .build(),

                // Accessibility
                DownloadSourcePolicy.builder()
                        .pattern("nvaccess.org")
                        .policyType(DownloadSourcePolicy.PolicyType.ALLOW)
                        .priority(20)
                        .description("NVDA screen reader")
                        .createdBy("SYSTEM")
                        .build(),

                // Block known malicious patterns
                DownloadSourcePolicy.builder()
                        .pattern("*.ru")
                        .policyType(DownloadSourcePolicy.PolicyType.DENY)
                        .priority(1)
                        .description("Block .ru domains (high-risk TLD)")
                        .createdBy("SYSTEM")
                        .build(),
                DownloadSourcePolicy.builder()
                        .pattern("*.cn")
                        .policyType(DownloadSourcePolicy.PolicyType.DENY)
                        .priority(1)
                        .description("Block .cn domains (high-risk TLD)")
                        .createdBy("SYSTEM")
                        .build(),
                DownloadSourcePolicy.builder()
                        .pattern("*torrent*")
                        .policyType(DownloadSourcePolicy.PolicyType.DENY)
                        .priority(5)
                        .description("Block torrent-related sites")
                        .createdBy("SYSTEM")
                        .build(),
                DownloadSourcePolicy.builder()
                        .pattern("*crack*")
                        .policyType(DownloadSourcePolicy.PolicyType.DENY)
                        .priority(5)
                        .description("Block sites with 'crack' in URL")
                        .createdBy("SYSTEM")
                        .build(),
                DownloadSourcePolicy.builder()
                        .pattern("*warez*")
                        .policyType(DownloadSourcePolicy.PolicyType.DENY)
                        .priority(5)
                        .description("Block warez sites")
                        .createdBy("SYSTEM")
                        .build()
        );

        policyRepository.saveAll(defaultPolicies);
        log.info("Created {} default download source policies", defaultPolicies.size());
    }

    /**
     * Check if a download URL is allowed by security policies.
     *
     * @param url The URL to check
     * @return SecurityCheckResult indicating whether the download is allowed
     */
    public SecurityCheckResult checkDownloadUrl(String url) {
        if (url == null || url.isEmpty()) {
            return SecurityCheckResult.denied("Empty URL");
        }

        // Validate URL format
        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                // Allow file:// for local paths
                if (!scheme.equals("file") && !url.startsWith("\\\\")) {
                    return SecurityCheckResult.denied("Invalid URL scheme: " + scheme);
                }
            }

            // Require HTTPS for remote downloads
            if (scheme != null && scheme.equals("http")) {
                log.warn("Insecure HTTP download URL: {}", url);
                // Don't block, but log warning - could be made configurable
            }
        } catch (Exception e) {
            // If it's not a valid URL, check if it's a local path
            if (!url.startsWith("\\\\") && !url.matches("^[A-Za-z]:.*")) {
                return SecurityCheckResult.denied("Invalid URL format: " + e.getMessage());
            }
        }

        // Get all active policies sorted by priority
        List<DownloadSourcePolicy> policies = policyRepository.findByIsActiveTrueOrderByPriorityAsc();

        // Check against each policy in priority order
        for (DownloadSourcePolicy policy : policies) {
            if (policy.matches(url)) {
                if (policy.getPolicyType() == DownloadSourcePolicy.PolicyType.DENY) {
                    log.warn("Download URL blocked by policy: {} -> {}", url, policy.getDescription());
                    return SecurityCheckResult.deniedByPolicy(policy);
                } else {
                    log.debug("Download URL allowed by policy: {} -> {}", url, policy.getDescription());
                    return SecurityCheckResult.allowedByPolicy(policy);
                }
            }
        }

        // Default behavior: allow if no policy matches (can be configured to deny by default)
        log.debug("Download URL allowed (no matching policy): {}", url);
        return SecurityCheckResult.allowed("No blocking policy found");
    }

    /**
     * Validate a third-party app's download source.
     */
    public SecurityCheckResult validateAppDownloadSource(ThirdPartyApp app) {
        String downloadUrl = app.getDownloadUrl();
        String localPath = app.getLocalPath();

        // Check download URL if present
        if (downloadUrl != null && !downloadUrl.isEmpty()) {
            SecurityCheckResult result = checkDownloadUrl(downloadUrl);
            if (!result.isAllowed()) {
                return result;
            }
        }

        // Local paths are generally trusted (they're on the IT network)
        if (localPath != null && !localPath.isEmpty()) {
            // Validate it's not trying to access system directories
            String normalizedPath = localPath.toLowerCase();
            if (normalizedPath.contains("..") ||
                normalizedPath.startsWith("c:\\windows") ||
                normalizedPath.startsWith("c:\\program files\\common")) {
                return SecurityCheckResult.denied("Local path attempts to access restricted directory");
            }
        }

        return SecurityCheckResult.allowed("Download source validated");
    }

    /**
     * Add a new security policy.
     */
    @Transactional
    public DownloadSourcePolicy addPolicy(String pattern, DownloadSourcePolicy.PolicyType type,
                                          int priority, String description, User createdBy) {
        if (policyRepository.existsByPatternIgnoreCase(pattern)) {
            throw new IllegalArgumentException("Policy with this pattern already exists");
        }

        DownloadSourcePolicy policy = DownloadSourcePolicy.builder()
                .pattern(pattern)
                .policyType(type)
                .priority(priority)
                .description(description)
                .createdBy(createdBy.getUsername())
                .build();

        DownloadSourcePolicy saved = policyRepository.save(policy);

        auditLogService.log(
                AuditAction.SECURITY_SETTINGS_CHANGE,
                createdBy.getUsername(),
                "Added download source policy: " + type + " " + pattern
        );

        return saved;
    }

    /**
     * Remove a security policy.
     */
    @Transactional
    public void removePolicy(Long policyId, User removedBy) {
        Optional<DownloadSourcePolicy> policy = policyRepository.findById(policyId);
        if (policy.isPresent()) {
            policyRepository.deleteById(policyId);

            auditLogService.log(
                    AuditAction.SECURITY_SETTINGS_CHANGE,
                    removedBy.getUsername(),
                    "Removed download source policy: " + policy.get().getPattern()
            );
        }
    }

    /**
     * Toggle a policy's active status.
     */
    @Transactional
    public DownloadSourcePolicy togglePolicy(Long policyId, User changedBy) {
        DownloadSourcePolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found"));

        policy.setIsActive(!policy.getIsActive());
        policy.setUpdatedAt(LocalDateTime.now());

        DownloadSourcePolicy saved = policyRepository.save(policy);

        auditLogService.log(
                AuditAction.SECURITY_SETTINGS_CHANGE,
                changedBy.getUsername(),
                (saved.getIsActive() ? "Enabled" : "Disabled") + " download source policy: " + policy.getPattern()
        );

        return saved;
    }

    /**
     * Get all policies for management UI.
     */
    public List<DownloadSourcePolicy> getAllPolicies() {
        return policyRepository.findAllByOrderByPriorityAsc();
    }

    /**
     * Get only active policies.
     */
    public List<DownloadSourcePolicy> getActivePolicies() {
        return policyRepository.findByIsActiveTrueOrderByPriorityAsc();
    }
}
