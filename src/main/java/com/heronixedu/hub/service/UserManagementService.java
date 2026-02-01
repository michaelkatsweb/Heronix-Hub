package com.heronixedu.hub.service;

import com.heronixedu.hub.model.Role;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.AuditAction;
import com.heronixedu.hub.repository.RoleRepository;
import com.heronixedu.hub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public User createUser(String username, String password, String fullName, String email,
                           Long roleId, User createdBy) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists: " + username);
        }

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role.getRoleName());
        user.setRoleEntity(role);
        user.setIsActive(true);
        user.setCreatedBy(createdBy.getId());

        User savedUser = userRepository.save(user);

        auditLogService.logUserChange(createdBy, savedUser, AuditAction.USER_CREATE,
                String.format("Created user %s with role %s", username, role.getDisplayName()));

        log.info("User created: {} by {}", username, createdBy.getUsername());
        return savedUser;
    }

    @Transactional
    public User updateUser(Long userId, String fullName, String email, Long roleId,
                           Boolean isActive, User updatedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        StringBuilder changes = new StringBuilder();

        if (fullName != null && !fullName.equals(user.getFullName())) {
            changes.append("fullName: ").append(user.getFullName()).append(" -> ").append(fullName).append("; ");
            user.setFullName(fullName);
        }

        if (email != null && !email.equals(user.getEmail())) {
            changes.append("email: ").append(user.getEmail()).append(" -> ").append(email).append("; ");
            user.setEmail(email);
        }

        if (roleId != null) {
            Role newRole = roleRepository.findById(roleId)
                    .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));
            if (!newRole.getRoleName().equals(user.getRole())) {
                changes.append("role: ").append(user.getRole()).append(" -> ").append(newRole.getRoleName()).append("; ");
                user.setRole(newRole.getRoleName());
                user.setRoleEntity(newRole);

                auditLogService.logUserChange(updatedBy, user, AuditAction.USER_ROLE_CHANGE,
                        String.format("Role changed to %s", newRole.getDisplayName()));
            }
        }

        if (isActive != null && !isActive.equals(user.getIsActive())) {
            changes.append("isActive: ").append(user.getIsActive()).append(" -> ").append(isActive).append("; ");
            user.setIsActive(isActive);

            auditLogService.logUserChange(updatedBy, user,
                    isActive ? AuditAction.USER_ACTIVATE : AuditAction.USER_DEACTIVATE,
                    isActive ? "User activated" : "User deactivated");
        }

        User savedUser = userRepository.save(user);

        if (changes.length() > 0) {
            auditLogService.logUserChange(updatedBy, savedUser, AuditAction.USER_UPDATE, changes.toString());
        }

        log.info("User updated: {} by {}", user.getUsername(), updatedBy.getUsername());
        return savedUser;
    }

    @Transactional
    public void deleteUser(Long userId, User deletedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (user.getId().equals(deletedBy.getId())) {
            throw new RuntimeException("Cannot delete your own account");
        }

        String username = user.getUsername();
        userRepository.delete(user);

        auditLogService.logUserChange(deletedBy, user, AuditAction.USER_DELETE,
                String.format("Deleted user %s", username));

        log.info("User deleted: {} by {}", username, deletedBy.getUsername());
    }

    @Transactional
    public void changePassword(Long userId, String newPassword, User changedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        auditLogService.logUserChange(changedBy, user, AuditAction.USER_UPDATE, "Password changed");

        log.info("Password changed for user: {} by {}", user.getUsername(), changedBy.getUsername());
    }

    @Transactional
    public void assignRole(Long userId, Long roleId, User assignedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));

        String oldRole = user.getRole();
        user.setRole(role.getRoleName());
        user.setRoleEntity(role);
        userRepository.save(user);

        auditLogService.logUserChange(assignedBy, user, AuditAction.USER_ROLE_CHANGE,
                String.format("Role changed from %s to %s", oldRole, role.getRoleName()));

        log.info("Role assigned: {} -> {} by {}", user.getUsername(), role.getRoleName(), assignedBy.getUsername());
    }

    public void updateLastLogin(User user) {
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
    }

    public long getUserCount() {
        return userRepository.count();
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }
}
