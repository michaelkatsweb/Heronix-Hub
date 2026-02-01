package com.heronixedu.hub.model.enums;

import java.util.EnumSet;
import java.util.Set;

public enum RoleType {
    SUPERADMIN("Super Administrator", EnumSet.allOf(PermissionType.class)),
    IT_ADMIN("IT Administrator", EnumSet.of(
            PermissionType.CAN_INSTALL,
            PermissionType.CAN_CONFIGURE_NETWORK,
            PermissionType.CAN_VIEW_STATUS,
            PermissionType.CAN_LAUNCH_APPS,
            PermissionType.CAN_MANAGE_SOFTWARE_CATALOG,
            PermissionType.CAN_APPROVE_SOFTWARE,
            PermissionType.CAN_INSTALL_THIRD_PARTY,
            PermissionType.CAN_VIEW_SOFTWARE_CATALOG
    )),
    TEACHER("Teacher", EnumSet.of(
            PermissionType.CAN_LAUNCH_APPS,
            PermissionType.CAN_VIEW_SOFTWARE_CATALOG,
            PermissionType.CAN_INSTALL_THIRD_PARTY
    )),
    STUDENT("Student", EnumSet.of(
            PermissionType.CAN_LAUNCH_APPS,
            PermissionType.CAN_VIEW_SOFTWARE_CATALOG
    ));

    private final String displayName;
    private final Set<PermissionType> permissions;

    RoleType(String displayName, Set<PermissionType> permissions) {
        this.displayName = displayName;
        this.permissions = permissions;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<PermissionType> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(PermissionType permission) {
        return permissions.contains(permission);
    }

    public boolean isAdmin() {
        return this == SUPERADMIN || this == IT_ADMIN;
    }

    public boolean canAccessAdminPanel() {
        return hasPermission(PermissionType.CAN_MANAGE_USERS) ||
                hasPermission(PermissionType.CAN_MANAGE_PRODUCTS) ||
                hasPermission(PermissionType.CAN_CONFIGURE_NETWORK) ||
                hasPermission(PermissionType.CAN_VIEW_LOGS) ||
                hasPermission(PermissionType.CAN_MANAGE_SOFTWARE_CATALOG);
    }

    public boolean canAccessSoftwareCatalog() {
        return hasPermission(PermissionType.CAN_VIEW_SOFTWARE_CATALOG) ||
                hasPermission(PermissionType.CAN_MANAGE_SOFTWARE_CATALOG);
    }
}
