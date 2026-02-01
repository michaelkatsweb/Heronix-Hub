package com.heronixedu.hub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Configuration for kiosk mode operation.
 * When enabled, Hub runs as the primary application interface,
 * restricting access to the underlying OS environment.
 */
@Entity
@Table(name = "kiosk_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KioskConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Whether kiosk mode is enabled.
     */
    @Column(name = "kiosk_enabled")
    @Builder.Default
    private Boolean kioskEnabled = false;

    /**
     * Start Hub in full screen mode.
     */
    @Column(name = "full_screen")
    @Builder.Default
    private Boolean fullScreen = true;

    /**
     * Hide the Windows taskbar when Hub is running.
     */
    @Column(name = "hide_taskbar")
    @Builder.Default
    private Boolean hideTaskbar = true;

    /**
     * Disable Alt+Tab and other OS shortcuts.
     */
    @Column(name = "disable_os_shortcuts")
    @Builder.Default
    private Boolean disableOsShortcuts = true;

    /**
     * Allow users to minimize the Hub window.
     */
    @Column(name = "allow_minimize")
    @Builder.Default
    private Boolean allowMinimize = false;

    /**
     * Show only installed applications (hide uninstalled).
     */
    @Column(name = "show_only_installed")
    @Builder.Default
    private Boolean showOnlyInstalled = true;

    /**
     * Hide the Heronix products section (show only third-party apps).
     */
    @Column(name = "hide_heronix_products")
    @Builder.Default
    private Boolean hideHeronixProducts = false;

    /**
     * Hide the logout button (requires admin to close).
     */
    @Column(name = "hide_logout_button")
    @Builder.Default
    private Boolean hideLogoutButton = false;

    /**
     * Auto-login with a specific user (for shared devices).
     */
    @Column(name = "auto_login_enabled")
    @Builder.Default
    private Boolean autoLoginEnabled = false;

    /**
     * Username for auto-login.
     */
    @Column(name = "auto_login_username", length = 100)
    private String autoLoginUsername;

    /**
     * Idle timeout in minutes (0 = disabled).
     * After this time of inactivity, return to login screen.
     */
    @Column(name = "idle_timeout_minutes")
    @Builder.Default
    private Integer idleTimeoutMinutes = 0;

    /**
     * Custom welcome message for the dashboard.
     */
    @Column(name = "welcome_message", length = 500)
    private String welcomeMessage;

    /**
     * Custom organization name to display.
     */
    @Column(name = "organization_name", length = 200)
    private String organizationName;

    /**
     * Path to custom logo image.
     */
    @Column(name = "custom_logo_path", length = 500)
    private String customLogoPath;

    /**
     * Primary color for branding (hex).
     */
    @Column(name = "primary_color", length = 7)
    @Builder.Default
    private String primaryColor = "#2196F3";

    /**
     * Restrict which application categories are visible.
     * Comma-separated list of category names, or empty for all.
     */
    @Column(name = "allowed_categories", length = 1000)
    private String allowedCategories;

    /**
     * Time restrictions - start hour (0-23).
     */
    @Column(name = "time_restrict_start")
    private Integer timeRestrictStart;

    /**
     * Time restrictions - end hour (0-23).
     */
    @Column(name = "time_restrict_end")
    private Integer timeRestrictEnd;

    /**
     * Days of week when access is allowed (comma-separated: MON,TUE,WED,THU,FRI).
     */
    @Column(name = "allowed_days", length = 50)
    private String allowedDays;

    /**
     * Who last modified this configuration.
     */
    @Column(name = "modified_by", length = 100)
    private String modifiedBy;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Check if access is allowed at the current time.
     */
    public boolean isAccessAllowedNow() {
        if (timeRestrictStart == null || timeRestrictEnd == null) {
            return true;
        }

        int currentHour = LocalDateTime.now().getHour();

        if (timeRestrictStart <= timeRestrictEnd) {
            // Normal range (e.g., 8 to 17)
            return currentHour >= timeRestrictStart && currentHour < timeRestrictEnd;
        } else {
            // Overnight range (e.g., 22 to 6)
            return currentHour >= timeRestrictStart || currentHour < timeRestrictEnd;
        }
    }

    /**
     * Check if access is allowed on the current day.
     */
    public boolean isDayAllowed() {
        if (allowedDays == null || allowedDays.isEmpty()) {
            return true;
        }

        String today = java.time.LocalDate.now().getDayOfWeek().name().substring(0, 3);
        return allowedDays.toUpperCase().contains(today);
    }

    /**
     * Check if a category is allowed.
     */
    public boolean isCategoryAllowed(String categoryName) {
        if (allowedCategories == null || allowedCategories.isEmpty()) {
            return true;
        }
        return allowedCategories.toLowerCase().contains(categoryName.toLowerCase());
    }
}
