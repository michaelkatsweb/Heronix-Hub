package com.heronixedu.hub.service;

import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.PermissionType;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    public boolean hasPermission(User user, PermissionType permission) {
        if (user == null) {
            return false;
        }

        // Check via roleEntity if available
        if (user.getRoleEntity() != null) {
            return user.getRoleEntity().hasPermission(permission.name());
        }

        // Fallback to legacy role check - SUPERADMIN has all permissions
        if ("SUPERADMIN".equals(user.getRole())) {
            return true;
        }

        // IT_ADMIN has specific permissions
        if ("IT_ADMIN".equals(user.getRole())) {
            return permission == PermissionType.CAN_INSTALL ||
                    permission == PermissionType.CAN_CONFIGURE_NETWORK ||
                    permission == PermissionType.CAN_VIEW_STATUS ||
                    permission == PermissionType.CAN_LAUNCH_APPS ||
                    permission == PermissionType.CAN_MANAGE_SOFTWARE_CATALOG ||
                    permission == PermissionType.CAN_APPROVE_SOFTWARE ||
                    permission == PermissionType.CAN_INSTALL_THIRD_PARTY ||
                    permission == PermissionType.CAN_VIEW_SOFTWARE_CATALOG ||
                    permission == PermissionType.CAN_MANAGE_DEVICES;
        }

        // TEACHER can launch apps and install approved third-party software
        if ("TEACHER".equals(user.getRole())) {
            return permission == PermissionType.CAN_LAUNCH_APPS ||
                    permission == PermissionType.CAN_VIEW_SOFTWARE_CATALOG ||
                    permission == PermissionType.CAN_INSTALL_THIRD_PARTY;
        }

        // STUDENT can launch apps and view software catalog
        if ("STUDENT".equals(user.getRole())) {
            return permission == PermissionType.CAN_LAUNCH_APPS ||
                    permission == PermissionType.CAN_VIEW_SOFTWARE_CATALOG;
        }

        return false;
    }

    public boolean canAccessAdminPanel(User user) {
        return hasPermission(user, PermissionType.CAN_MANAGE_USERS) ||
                hasPermission(user, PermissionType.CAN_MANAGE_PRODUCTS) ||
                hasPermission(user, PermissionType.CAN_CONFIGURE_NETWORK) ||
                hasPermission(user, PermissionType.CAN_VIEW_LOGS);
    }

    public boolean canInstall(User user) {
        return hasPermission(user, PermissionType.CAN_INSTALL);
    }

    public boolean canConfigureNetwork(User user) {
        return hasPermission(user, PermissionType.CAN_CONFIGURE_NETWORK);
    }

    public boolean canManageUsers(User user) {
        return hasPermission(user, PermissionType.CAN_MANAGE_USERS);
    }

    public boolean canManageProducts(User user) {
        return hasPermission(user, PermissionType.CAN_MANAGE_PRODUCTS);
    }

    public boolean canViewLogs(User user) {
        return hasPermission(user, PermissionType.CAN_VIEW_LOGS);
    }

    public boolean canManageRoles(User user) {
        return hasPermission(user, PermissionType.CAN_MANAGE_ROLES);
    }

    public boolean canLaunchApps(User user) {
        return hasPermission(user, PermissionType.CAN_LAUNCH_APPS);
    }

    public boolean canManageDevices(User user) {
        return hasPermission(user, PermissionType.CAN_MANAGE_DEVICES);
    }
}
