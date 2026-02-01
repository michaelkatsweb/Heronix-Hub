package com.heronixedu.hub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a download source policy entry for whitelisting or blacklisting
 * download domains/URLs for third-party applications.
 */
@Entity
@Table(name = "download_source_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadSourcePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The domain or URL pattern to match.
     * Examples: "microsoft.com", "*.github.com", "https://releases.example.com/*"
     */
    @Column(name = "pattern", nullable = false, length = 500)
    private String pattern;

    /**
     * Whether this is a whitelist (ALLOW) or blacklist (DENY) entry.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false)
    private PolicyType policyType;

    /**
     * Priority for rule evaluation. Lower numbers are evaluated first.
     * This allows for exceptions (e.g., block *.example.com but allow trusted.example.com)
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 100;

    /**
     * Human-readable description of why this rule exists.
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Whether this policy is currently active.
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Who created this policy entry.
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum PolicyType {
        ALLOW,  // Whitelist - explicitly allow this source
        DENY    // Blacklist - explicitly deny this source
    }

    /**
     * Check if this policy pattern matches the given URL.
     */
    public boolean matches(String url) {
        if (url == null || pattern == null) {
            return false;
        }

        String normalizedUrl = url.toLowerCase();
        String normalizedPattern = pattern.toLowerCase();

        // Handle wildcard patterns
        if (normalizedPattern.contains("*")) {
            // Convert wildcard pattern to regex
            String regex = normalizedPattern
                    .replace(".", "\\.")
                    .replace("*", ".*");
            return normalizedUrl.matches(regex);
        }

        // Domain-only pattern (match anywhere in URL)
        if (!normalizedPattern.contains("/")) {
            try {
                java.net.URI uri = java.net.URI.create(url);
                String host = uri.getHost();
                if (host != null) {
                    return host.toLowerCase().endsWith(normalizedPattern) ||
                           host.toLowerCase().equals(normalizedPattern);
                }
            } catch (Exception e) {
                // Fall through to simple contains check
            }
        }

        // Exact or prefix match
        return normalizedUrl.contains(normalizedPattern);
    }
}
