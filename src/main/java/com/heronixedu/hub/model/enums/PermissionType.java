package com.heronixedu.hub.model.enums;

public enum PermissionType {
    CAN_INSTALL("Install Applications", "Can download and install applications from server", "DEPLOYMENT"),
    CAN_CONFIGURE_NETWORK("Configure Network", "Can modify network settings including proxy and DNS", "NETWORK"),
    CAN_MANAGE_USERS("Manage Users", "Can create, edit, and delete user accounts", "ADMIN"),
    CAN_MANAGE_PRODUCTS("Manage Products", "Can add, edit, and remove products from catalog", "ADMIN"),
    CAN_VIEW_LOGS("View Audit Logs", "Can view system audit logs", "ADMIN"),
    CAN_MANAGE_ROLES("Manage Roles", "Can create and modify roles and permissions", "ADMIN"),
    CAN_VIEW_STATUS("View System Status", "Can view system health and status", "MONITORING"),
    CAN_LAUNCH_APPS("Launch Applications", "Can launch installed applications", "BASIC"),
    CAN_MANAGE_SOFTWARE_CATALOG("Manage Software Catalog", "Can add, edit, and remove third-party apps from catalog", "SOFTWARE"),
    CAN_APPROVE_SOFTWARE("Approve Software", "Can approve third-party apps for deployment", "SOFTWARE"),
    CAN_INSTALL_THIRD_PARTY("Install Third-Party Apps", "Can install approved third-party applications", "SOFTWARE"),
    CAN_VIEW_SOFTWARE_CATALOG("View Software Catalog", "Can view the third-party software catalog", "SOFTWARE"),
    CAN_MANAGE_DEVICES("Manage Devices", "Can approve, reject, and manage registered devices", "ADMIN");

    private final String displayName;
    private final String description;
    private final String category;

    PermissionType(String displayName, String description, String category) {
        this.displayName = displayName;
        this.description = description;
        this.category = category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }
}
