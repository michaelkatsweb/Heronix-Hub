package com.heronixedu.hub.model.enums;

/**
 * Defines how updates are handled for third-party applications.
 */
public enum UpdatePolicy {
    AUTO("Auto Update", "Automatically download and install updates when available"),
    ADMIN_APPROVED("Admin Approved", "Updates require IT admin approval before installation"),
    MANUAL("Manual", "Users must manually trigger updates"),
    DISABLED("Disabled", "Updates are disabled for this application");

    private final String displayName;
    private final String description;

    UpdatePolicy(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
