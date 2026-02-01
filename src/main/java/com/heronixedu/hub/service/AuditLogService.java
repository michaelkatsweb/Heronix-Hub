package com.heronixedu.hub.service;

import com.heronixedu.hub.model.AuditLog;
import com.heronixedu.hub.model.Product;
import com.heronixedu.hub.model.ThirdPartyApp;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.AuditAction;
import com.heronixedu.hub.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final SecureAuditClient secureAuditClient;

    // Set by AuthenticationService after login to enable server-side mirroring
    private String sisServerUrl;
    private String sisAccessToken;

    public void setSisSession(String serverUrl, String accessToken) {
        this.sisServerUrl = serverUrl;
        this.sisAccessToken = accessToken;
    }

    public void clearSisSession() {
        this.sisServerUrl = null;
        this.sisAccessToken = null;
    }

    private void mirrorToServer(String action, String username, String details, boolean success, String severity) {
        if (sisServerUrl == null) return;
        String token = sisAccessToken;
        if (token == null) {
            try {
                java.nio.file.Path tokenPath = java.nio.file.Paths.get(
                        System.getProperty("user.home"), ".heronix", "auth", "token.jwt");
                if (java.nio.file.Files.exists(tokenPath)) {
                    token = java.nio.file.Files.readString(tokenPath).trim();
                }
            } catch (Exception ignored) {}
        }
        if (token == null) return;
        secureAuditClient.sendAuditEvent(sisServerUrl, token, action, username, details, success, severity);
    }

    public void log(AuditAction action, String username, String details) {
        AuditLog auditLog = AuditLog.builder()
                .action(action)
                .username(username)
                .details(details)
                .success(true)
                .severity("INFO")
                .build();
        auditLogRepository.save(auditLog);
        log.info("Audit: {} - {} - {}", action, username, details);
        mirrorToServer(action.name(), username, details, true, "INFO");
    }

    public void logLogin(String username, boolean success, String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
                .username(username)
                .action(success ? AuditAction.LOGIN_SUCCESS : AuditAction.LOGIN_FAILURE)
                .ipAddress(ipAddress)
                .success(success)
                .severity(success ? "INFO" : "WARNING")
                .details(success ? "User logged in successfully" : "Failed login attempt")
                .build();
        auditLogRepository.save(auditLog);
        log.info("Login {}: {} from {}", success ? "success" : "failure", username, ipAddress);
        mirrorToServer(success ? "LOGIN_SUCCESS" : "LOGIN_FAILURE", username,
                (success ? "User logged in from " : "Failed login attempt from ") + ipAddress,
                success, success ? "INFO" : "WARNING");
    }

    public void logLogout(String username) {
        AuditLog auditLog = AuditLog.builder()
                .username(username)
                .action(AuditAction.LOGOUT)
                .success(true)
                .severity("INFO")
                .details("User logged out")
                .build();
        auditLogRepository.save(auditLog);
        log.info("Logout: {}", username);
        mirrorToServer("LOGOUT", username, "User logged out", true, "INFO");
    }

    public void logUserChange(User actor, User target, AuditAction action, String details) {
        AuditLog auditLog = AuditLog.builder()
                .username(actor.getUsername())
                .action(action)
                .entityType("User")
                .entityId(target.getId())
                .details(details)
                .success(true)
                .severity("INFO")
                .build();
        auditLogRepository.save(auditLog);
        log.info("User change by {}: {} on user {} - {}", actor.getUsername(), action, target.getUsername(), details);
        mirrorToServer(action.name(), actor.getUsername(), "User " + target.getUsername() + ": " + details, true, "INFO");
    }

    public void logProductInstall(User user, Product product, boolean success, String details) {
        AuditLog auditLog = AuditLog.builder()
                .username(user.getUsername())
                .action(success ? AuditAction.PRODUCT_INSTALL_COMPLETE : AuditAction.PRODUCT_INSTALL_FAILED)
                .entityType("Product")
                .entityId(product.getId())
                .details(details)
                .success(success)
                .severity(success ? "INFO" : "ERROR")
                .build();
        auditLogRepository.save(auditLog);
        log.info("Product install {}: {} by {} - {}", success ? "success" : "failed", product.getProductName(), user.getUsername(), details);
        mirrorToServer(auditLog.getAction().name(), user.getUsername(), product.getProductName() + ": " + details, success, success ? "INFO" : "ERROR");
    }

    public void logProductLaunch(User user, Product product) {
        AuditLog auditLog = AuditLog.builder()
                .username(user.getUsername())
                .action(AuditAction.PRODUCT_LAUNCH)
                .entityType("Product")
                .entityId(product.getId())
                .details("Launched " + product.getProductName())
                .success(true)
                .severity("INFO")
                .build();
        auditLogRepository.save(auditLog);
        log.info("Product launched: {} by {}", product.getProductName(), user.getUsername());
        mirrorToServer("PRODUCT_LAUNCH", user.getUsername(), "Launched " + product.getProductName(), true, "INFO");
    }

    public void logNetworkConfigChange(User user, String details) {
        AuditLog auditLog = AuditLog.builder()
                .username(user.getUsername())
                .action(AuditAction.NETWORK_CONFIG_CHANGE)
                .entityType("NetworkConfig")
                .details(details)
                .success(true)
                .severity("INFO")
                .build();
        auditLogRepository.save(auditLog);
        log.info("Network config changed by {}: {}", user.getUsername(), details);
        mirrorToServer("NETWORK_CONFIG_CHANGE", user.getUsername(), details, true, "INFO");
    }

    public void logServerConnectionTest(User user, boolean success, String details) {
        AuditLog auditLog = AuditLog.builder()
                .username(user.getUsername())
                .action(AuditAction.SERVER_CONNECTION_TEST)
                .entityType("NetworkConfig")
                .details(details)
                .success(success)
                .severity(success ? "INFO" : "WARNING")
                .build();
        auditLogRepository.save(auditLog);
        log.info("Server connection test by {}: {} - {}", user.getUsername(), success ? "passed" : "failed", details);
        mirrorToServer("SERVER_CONNECTION_TEST", user.getUsername(), details, success, success ? "INFO" : "WARNING");
    }

    public Page<AuditLog> getLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    public Page<AuditLog> searchByUsername(String username, Pageable pageable) {
        return auditLogRepository.findByUsernameContainingIgnoreCase(username, pageable);
    }

    public Page<AuditLog> searchByAction(AuditAction action, Pageable pageable) {
        return auditLogRepository.findByAction(action, pageable);
    }

    public Page<AuditLog> searchByDateRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return auditLogRepository.findByTimestampBetween(start, end, pageable);
    }

    public List<AuditLog> getRecentLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc();
    }

    // Third-party app audit methods

    public void logThirdPartyAppAdd(User user, ThirdPartyApp app, String details) {
        AuditLog auditLog = AuditLog.builder()
                .username(user.getUsername())
                .action(AuditAction.THIRD_PARTY_APP_ADD)
                .entityType("ThirdPartyApp")
                .entityId(app.getId())
                .details(details)
                .success(true)
                .severity("INFO")
                .build();
        auditLogRepository.save(auditLog);
        log.info("Third-party app added by {}: {} - {}", user.getUsername(), app.getAppName(), details);
        mirrorToServer("THIRD_PARTY_APP_ADD", user.getUsername(), app.getAppName() + ": " + details, true, "INFO");
    }

    public void logThirdPartyAppApprove(User user, ThirdPartyApp app, boolean approved, String details) {
        AuditLog auditLog = AuditLog.builder()
                .username(user.getUsername())
                .action(approved ? AuditAction.THIRD_PARTY_APP_APPROVE : AuditAction.THIRD_PARTY_APP_REVOKE)
                .entityType("ThirdPartyApp")
                .entityId(app.getId())
                .details(details)
                .success(true)
                .severity("INFO")
                .build();
        auditLogRepository.save(auditLog);
        log.info("Third-party app {} by {}: {} - {}", approved ? "approved" : "revoked",
                user.getUsername(), app.getAppName(), details);
        mirrorToServer(approved ? "THIRD_PARTY_APP_APPROVE" : "THIRD_PARTY_APP_REVOKE",
                user.getUsername(), app.getAppName() + ": " + details, true, "INFO");
    }

    public void logThirdPartyInstall(User user, ThirdPartyApp app, boolean success, String details) {
        AuditLog auditLog = AuditLog.builder()
                .username(user.getUsername())
                .action(success ? AuditAction.THIRD_PARTY_INSTALL_COMPLETE : AuditAction.THIRD_PARTY_INSTALL_FAILED)
                .entityType("ThirdPartyApp")
                .entityId(app.getId())
                .details(details)
                .success(success)
                .severity(success ? "INFO" : "ERROR")
                .build();
        auditLogRepository.save(auditLog);
        log.info("Third-party install {}: {} by {} - {}", success ? "success" : "failed",
                app.getAppName(), user.getUsername(), details);
        mirrorToServer(auditLog.getAction().name(), user.getUsername(), app.getAppName() + ": " + details, success, success ? "INFO" : "ERROR");
    }

    public void logThirdPartyUninstall(User user, ThirdPartyApp app, boolean success, String details) {
        AuditLog auditLog = AuditLog.builder()
                .username(user.getUsername())
                .action(success ? AuditAction.THIRD_PARTY_UNINSTALL_COMPLETE : AuditAction.THIRD_PARTY_UNINSTALL_FAILED)
                .entityType("ThirdPartyApp")
                .entityId(app.getId())
                .details(details)
                .success(success)
                .severity(success ? "INFO" : "ERROR")
                .build();
        auditLogRepository.save(auditLog);
        log.info("Third-party uninstall {}: {} by {} - {}", success ? "success" : "failed",
                app.getAppName(), user.getUsername(), details);
        mirrorToServer(auditLog.getAction().name(), user.getUsername(), app.getAppName() + ": " + details, success, success ? "INFO" : "ERROR");
    }
}
