package com.heronixedu.hub.model.enums;

public enum AuditAction {
    // Authentication
    LOGIN_SUCCESS("User logged in successfully"),
    LOGIN_FAILURE("Failed login attempt"),
    LOGOUT("User logged out"),

    // User Management
    USER_CREATE("New user created"),
    USER_UPDATE("User updated"),
    USER_DELETE("User deleted"),
    USER_ROLE_CHANGE("User role changed"),
    USER_ACTIVATE("User activated"),
    USER_DEACTIVATE("User deactivated"),

    // Product Management
    PRODUCT_INSTALL_START("Product installation started"),
    PRODUCT_INSTALL_COMPLETE("Product installation completed"),
    PRODUCT_INSTALL_FAILED("Product installation failed"),
    PRODUCT_UNINSTALL("Product uninstalled"),
    PRODUCT_LAUNCH("Product launched"),
    PRODUCT_UPDATE("Product updated"),

    // Network Configuration
    NETWORK_CONFIG_CHANGE("Network configuration changed"),
    SERVER_CONNECTION_TEST("Server connection tested"),

    // Third-Party Software
    THIRD_PARTY_APP_ADD("Third-party app added to catalog"),
    THIRD_PARTY_APP_UPDATE("Third-party app updated"),
    THIRD_PARTY_APP_REMOVE("Third-party app removed from catalog"),
    THIRD_PARTY_APP_APPROVE("Third-party app approved"),
    THIRD_PARTY_APP_REVOKE("Third-party app approval revoked"),
    THIRD_PARTY_INSTALL_START("Third-party app installation started"),
    THIRD_PARTY_INSTALL_COMPLETE("Third-party app installation completed"),
    THIRD_PARTY_INSTALL_FAILED("Third-party app installation failed"),
    THIRD_PARTY_UNINSTALL_COMPLETE("Third-party app uninstalled"),
    THIRD_PARTY_UNINSTALL_FAILED("Third-party app uninstall failed"),

    // Security
    SECURITY_SETTINGS_CHANGE("Security settings changed"),
    SIGNATURE_VERIFICATION_FAILED("Digital signature verification failed"),
    DOWNLOAD_SOURCE_BLOCKED("Download blocked by security policy"),
    VIRUS_SCAN_FAILED("Virus scan detected threat"),
    UPDATE_ROLLBACK("Application update rolled back"),

    // System
    SYSTEM_STATUS_CHECK("System status checked"),
    CONFIG_CHANGE("System configuration changed");

    private final String description;

    AuditAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
