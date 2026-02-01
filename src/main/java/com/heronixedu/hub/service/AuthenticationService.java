package com.heronixedu.hub.service;

import com.heronixedu.hub.exception.DeviceNotApprovedException;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final SisApiClient sisApiClient;
    private final DeviceApprovalService deviceApprovalService;
    private final AuditLogService auditLogService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    // Store the SIS server URL for the current session
    private String currentSisServerUrl;

    /**
     * Authenticate user - tries SIS server first, falls back to local admin
     * @return User object if successful, null otherwise
     */
    public User login(String username, String password) {
        try {
            log.info("Login attempt for user: {}", username);

            // First, try to authenticate against SIS server
            Optional<SisApiClient.SisAuthResult> sisResult = sisApiClient.authenticate(username, password);

            if (sisResult.isPresent()) {
                // User authenticated via SIS
                SisApiClient.SisAuthResult authResult = sisResult.get();
                log.info("User {} authenticated via SIS server at {}", username, authResult.getServerUrl());

                // Store the server URL for this session
                currentSisServerUrl = authResult.getServerUrl();

                // Enable server-side audit mirroring
                auditLogService.setSisSession(currentSisServerUrl, authResult.getAccessToken());

                // Create or update local user record for this SIS user
                User user = getOrCreateSisUser(username, authResult);

                // Log login (mirrors to server via secure audit)
                auditLogService.logLogin(username, true, "SIS:" + authResult.getServerUrl());

                // Generate and save Hub token
                String token = tokenService.generateToken(user);
                tokenService.saveTokenToFile(token);

                // Check device approval (SUPERADMIN and IT_ADMIN bypass)
                deviceApprovalService.checkDeviceApproval(
                        currentSisServerUrl, authResult.getAccessToken(),
                        username, user.getRole());

                // Update last login
                user.setLastLogin(LocalDateTime.now());
                userRepository.save(user);

                log.info("User logged in successfully via SIS: {}", username);
                return user;
            }

            // Fall back to local authentication (for local admin account)
            log.debug("SIS authentication failed or unavailable, trying local authentication for: {}", username);
            return localLogin(username, password);

        } catch (DeviceNotApprovedException e) {
            auditLogService.logLogin(username, false, "Device not approved");
            throw e;
        } catch (RuntimeException e) {
            auditLogService.logLogin(username, false, e.getMessage());
            log.error("Login failed for user: {}", username, e);
            throw e;
        }
    }

    /**
     * Local authentication (for admin account when SIS is unavailable)
     */
    private User localLogin(String username, String password) {
        // Find user by username in local database
        Optional<User> optionalUser = userRepository.findByUsername(username);

        if (optionalUser.isEmpty()) {
            log.warn("User not found locally and SIS unavailable: {}", username);
            throw new RuntimeException("Invalid username or password");
        }

        User user = optionalUser.get();

        // Check if user is active
        if (!user.getIsActive()) {
            log.warn("Login attempt for inactive user: {}", username);
            throw new RuntimeException("User account is inactive");
        }

        // Verify password (only for local users with password hash)
        if (user.getPasswordHash() != null && !user.getPasswordHash().isEmpty()) {
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                log.warn("Invalid password for local user: {}", username);
                throw new RuntimeException("Invalid username or password");
            }
        } else {
            // SIS user without local password - can't authenticate locally
            log.warn("SIS user {} cannot authenticate locally - SIS server unavailable", username);
            throw new RuntimeException("Server unavailable. Please try again later.");
        }

        // Generate and save token
        String token = tokenService.generateToken(user);
        tokenService.saveTokenToFile(token);

        // Update last login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        auditLogService.logLogin(username, true, "local");
        log.info("User logged in successfully (local): {}", username);
        return user;
    }

    /**
     * Get or create a local user record for an SIS-authenticated user
     */
    private User getOrCreateSisUser(String username, SisApiClient.SisAuthResult authResult) {
        Optional<User> existingUser = userRepository.findByUsername(username);

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            // Update role if changed
            String role = determineRoleFromSisRoles(authResult.getRoles());
            if (!role.equals(user.getRole())) {
                user.setRole(role);
                userRepository.save(user);
            }
            return user;
        }

        // Create new local user record
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPasswordHash(""); // SIS users don't have local passwords
        newUser.setFullName(formatFullName(username, authResult.getRoles()));
        newUser.setRole(determineRoleFromSisRoles(authResult.getRoles()));
        newUser.setIsActive(true);
        newUser.setCreatedAt(LocalDateTime.now());

        return userRepository.save(newUser);
    }

    /**
     * Determine Hub role from SIS roles/authorities
     */
    private String determineRoleFromSisRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "USER";
        }

        // Map SIS roles to Hub roles
        for (String role : roles) {
            String upperRole = role.toUpperCase().replace("ROLE_", "");
            if (upperRole.contains("ADMIN") || upperRole.contains("SUPERADMIN")) {
                return "SUPERADMIN";
            }
            if (upperRole.contains("IT") || upperRole.contains("TECH")) {
                return "IT_ADMIN";
            }
            if (upperRole.contains("TEACHER") || upperRole.contains("FACULTY")) {
                return "TEACHER";
            }
            if (upperRole.contains("STUDENT")) {
                return "STUDENT";
            }
        }

        return "USER";
    }

    /**
     * Format a display name from username and roles
     */
    private String formatFullName(String username, List<String> roles) {
        // If username looks like an ID (T001, S001), format nicely
        if (username.matches("[A-Z]\\d+")) {
            String prefix = username.substring(0, 1);
            String type = switch (prefix) {
                case "T" -> "Teacher";
                case "S" -> "Student";
                case "A" -> "Admin";
                default -> "User";
            };
            return type + " " + username;
        }
        // Capitalize username as name
        return username.substring(0, 1).toUpperCase() + username.substring(1);
    }

    /**
     * Logout user by deleting token file
     */
    public void logout() {
        try {
            tokenService.deleteTokenFile();
            auditLogService.clearSisSession();
            currentSisServerUrl = null;
            log.info("User logged out successfully");
        } catch (Exception e) {
            log.error("Error during logout", e);
            throw new RuntimeException("Failed to logout", e);
        }
    }

    /**
     * Check if there's a valid existing token
     */
    public User checkExistingToken() {
        String token = tokenService.readTokenFromFile();
        if (token == null || token.isEmpty()) {
            return null;
        }
        return tokenService.validateToken(token);
    }

    /**
     * Hash password using BCrypt
     */
    public String hashPassword(String password) {
        return passwordEncoder.encode(password);
    }

    /**
     * Check if SIS server is available
     */
    public boolean isSisAvailable() {
        return sisApiClient.isSisAvailable();
    }

    /**
     * Get the current SIS server URL
     */
    public String getCurrentSisServerUrl() {
        return currentSisServerUrl;
    }
}
