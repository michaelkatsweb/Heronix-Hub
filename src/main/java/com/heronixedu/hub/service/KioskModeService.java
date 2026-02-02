package com.heronixedu.hub.service;

import com.heronixedu.hub.model.KioskConfig;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.AuditAction;
import com.heronixedu.hub.repository.KioskConfigRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for managing kiosk mode functionality.
 * Provides centralized control for running Hub as the primary application interface.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KioskModeService {

    private final KioskConfigRepository configRepository;
    private final AuditLogService auditLogService;
    private final NativeKeyBlocker nativeKeyBlocker;
    private final ProcessGuardService processGuardService;

    private KioskConfig cachedConfig;
    private String activeRole;

    @PostConstruct
    public void init() {
        ensureConfigExists();
    }

    /**
     * Ensure a default configuration exists.
     */
    @Transactional
    public void ensureConfigExists() {
        if (configRepository.count() == 0) {
            KioskConfig defaultConfig = KioskConfig.builder()
                    .kioskEnabled(false)
                    .fullScreen(true)
                    .hideTaskbar(true)
                    .disableOsShortcuts(true)
                    .allowMinimize(false)
                    .showOnlyInstalled(true)
                    .hideHeronixProducts(false)
                    .hideLogoutButton(false)
                    .autoLoginEnabled(false)
                    .idleTimeoutMinutes(0)
                    .primaryColor("#2196F3")
                    .modifiedBy("SYSTEM")
                    .modifiedAt(LocalDateTime.now())
                    .build();
            configRepository.save(defaultConfig);
            log.info("Created default kiosk configuration");
        }
        cachedConfig = null;
    }

    /**
     * Get the current kiosk configuration.
     */
    public KioskConfig getConfig() {
        if (cachedConfig == null) {
            cachedConfig = configRepository.getConfig()
                    .orElseGet(() -> {
                        ensureConfigExists();
                        return configRepository.getConfig().orElseThrow();
                    });
        }
        return cachedConfig;
    }

    /**
     * Check if kiosk mode is enabled.
     */
    public boolean isKioskModeEnabled() {
        return Boolean.TRUE.equals(getConfig().getKioskEnabled());
    }

    /**
     * Update kiosk configuration.
     */
    @Transactional
    public KioskConfig updateConfig(KioskConfig newConfig, User updatedBy) {
        KioskConfig existing = getConfig();

        // Copy values from new config
        existing.setKioskEnabled(newConfig.getKioskEnabled());
        existing.setFullScreen(newConfig.getFullScreen());
        existing.setHideTaskbar(newConfig.getHideTaskbar());
        existing.setDisableOsShortcuts(newConfig.getDisableOsShortcuts());
        existing.setAllowMinimize(newConfig.getAllowMinimize());
        existing.setShowOnlyInstalled(newConfig.getShowOnlyInstalled());
        existing.setHideHeronixProducts(newConfig.getHideHeronixProducts());
        existing.setHideLogoutButton(newConfig.getHideLogoutButton());
        existing.setAutoLoginEnabled(newConfig.getAutoLoginEnabled());
        existing.setAutoLoginUsername(newConfig.getAutoLoginUsername());
        existing.setIdleTimeoutMinutes(newConfig.getIdleTimeoutMinutes());
        existing.setWelcomeMessage(newConfig.getWelcomeMessage());
        existing.setOrganizationName(newConfig.getOrganizationName());
        existing.setCustomLogoPath(newConfig.getCustomLogoPath());
        existing.setPrimaryColor(newConfig.getPrimaryColor());
        existing.setAllowedCategories(newConfig.getAllowedCategories());
        existing.setTimeRestrictStart(newConfig.getTimeRestrictStart());
        existing.setTimeRestrictEnd(newConfig.getTimeRestrictEnd());
        existing.setAllowedDays(newConfig.getAllowedDays());
        existing.setModifiedBy(updatedBy.getUsername());
        existing.setModifiedAt(LocalDateTime.now());

        KioskConfig saved = configRepository.save(existing);
        cachedConfig = saved;

        auditLogService.log(
                AuditAction.CONFIG_CHANGE,
                updatedBy.getUsername(),
                "Updated kiosk mode configuration (kiosk enabled: " + saved.getKioskEnabled() + ")"
        );

        log.info("Kiosk configuration updated by {}", updatedBy.getUsername());
        return saved;
    }

    /**
     * Enable or disable kiosk mode.
     */
    @Transactional
    public KioskConfig setKioskEnabled(boolean enabled, User changedBy) {
        KioskConfig config = getConfig();
        config.setKioskEnabled(enabled);
        config.setModifiedBy(changedBy.getUsername());
        config.setModifiedAt(LocalDateTime.now());

        KioskConfig saved = configRepository.save(config);
        cachedConfig = saved;

        auditLogService.log(
                AuditAction.CONFIG_CHANGE,
                changedBy.getUsername(),
                (enabled ? "Enabled" : "Disabled") + " kiosk mode"
        );

        log.info("Kiosk mode {} by {}", enabled ? "enabled" : "disabled", changedBy.getUsername());
        return saved;
    }

    /**
     * Apply kiosk mode settings to a stage for a specific user role.
     * Kiosk mode only activates for STUDENT role.
     */
    public void applyToStage(Stage stage, String userRole) {
        this.activeRole = userRole;
        applyToStage(stage);
    }

    /**
     * Apply kiosk mode settings to a stage.
     */
    public void applyToStage(Stage stage) {
        if (!isEffectivelyEnabled()) {
            return;
        }

        KioskConfig config = getConfig();

        Platform.runLater(() -> {
            // Full screen mode
            if (Boolean.TRUE.equals(config.getFullScreen())) {
                stage.setMaximized(true);
                stage.setFullScreen(true);
                stage.setFullScreenExitHint("");
                stage.setResizable(false);
            }

            // Always on top
            stage.setAlwaysOnTop(true);

            // Prevent closing (requires admin intervention)
            stage.setOnCloseRequest(event -> {
                if (isEffectivelyEnabled()) {
                    event.consume();
                    log.info("Close attempt blocked in kiosk mode");
                }
            });

            // Prevent minimize if configured
            if (!Boolean.TRUE.equals(config.getAllowMinimize())) {
                stage.iconifiedProperty().addListener((obs, wasMinimized, isMinimized) -> {
                    if (isMinimized && isEffectivelyEnabled()) {
                        Platform.runLater(() -> stage.setIconified(false));
                    }
                });
            }

            // Re-focus if window loses focus (prevent students navigating away)
            stage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!isFocused && isEffectivelyEnabled()) {
                    Platform.runLater(() -> {
                        stage.setAlwaysOnTop(true);
                        stage.toFront();
                        stage.requestFocus();
                    });
                }
            });

            // Block dangerous key combos at the JavaFX level
            stage.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    installKeyFilter(newScene);
                }
            });
            if (stage.getScene() != null) {
                installKeyFilter(stage.getScene());
            }

            // Enable native OS-level key blocking (Alt+Tab, Win key, etc.)
            if (Boolean.TRUE.equals(config.getDisableOsShortcuts())) {
                nativeKeyBlocker.start();
            }

            // Start process guard to kill Task Manager if students open it
            processGuardService.start();

            log.info("Kiosk mode settings applied to stage (role: {})", activeRole);
        });
    }

    /**
     * Install a key event filter on a scene to block escape-related shortcuts.
     */
    private void installKeyFilter(javafx.scene.Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!isEffectivelyEnabled()) return;

            // Block Alt+F4, Alt+Tab, Alt+Esc
            if (event.isAltDown() && (event.getCode() == KeyCode.F4 ||
                    event.getCode() == KeyCode.TAB ||
                    event.getCode() == KeyCode.ESCAPE)) {
                event.consume();
            }
            // Block Ctrl+Esc (Start menu)
            if (event.isControlDown() && event.getCode() == KeyCode.ESCAPE) {
                event.consume();
            }
            // Block Windows/Meta key
            if (event.getCode() == KeyCode.WINDOWS || event.getCode() == KeyCode.META) {
                event.consume();
            }
            // Block F11 (fullscreen toggle exit)
            if (event.getCode() == KeyCode.F11) {
                event.consume();
            }
            // Block Escape (fullscreen exit)
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
            }
        });
    }

    /**
     * Check if kiosk mode is effectively enabled (config enabled AND student role).
     */
    public boolean isEffectivelyEnabled() {
        if (!isKioskModeEnabled()) {
            return false;
        }
        // Only enforce kiosk for student role; admins/teachers can use desktop normally
        if (activeRole != null && !"STUDENT".equalsIgnoreCase(activeRole)) {
            return false;
        }
        return true;
    }

    @PreDestroy
    public void cleanup() {
        nativeKeyBlocker.stop();
        processGuardService.stop();
    }

    /**
     * Check if the current time allows access.
     */
    public boolean isAccessAllowed() {
        KioskConfig config = getConfig();
        return config.isAccessAllowedNow() && config.isDayAllowed();
    }

    /**
     * Get the access denied message if applicable.
     */
    public String getAccessDeniedMessage() {
        KioskConfig config = getConfig();

        if (!config.isDayAllowed()) {
            return "Access is not available today. Please try again on an allowed day.";
        }

        if (!config.isAccessAllowedNow()) {
            String start = config.getTimeRestrictStart() != null ?
                    String.format("%02d:00", config.getTimeRestrictStart()) : "";
            String end = config.getTimeRestrictEnd() != null ?
                    String.format("%02d:00", config.getTimeRestrictEnd()) : "";
            return "Access is only available between " + start + " and " + end + ".";
        }

        return null;
    }

    /**
     * Get idle timeout in milliseconds.
     */
    public long getIdleTimeoutMs() {
        Integer minutes = getConfig().getIdleTimeoutMinutes();
        if (minutes == null || minutes <= 0) {
            return 0;
        }
        return minutes * 60L * 1000L;
    }

    /**
     * Clear the cached configuration (force reload).
     */
    public void clearCache() {
        cachedConfig = null;
    }
}
