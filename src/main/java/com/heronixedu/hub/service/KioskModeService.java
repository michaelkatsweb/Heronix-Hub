package com.heronixedu.hub.service;

import com.heronixedu.hub.model.KioskConfig;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.AuditAction;
import com.heronixedu.hub.repository.KioskConfigRepository;
import jakarta.annotation.PostConstruct;
import javafx.application.Platform;
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

    private KioskConfig cachedConfig;

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
     * Apply kiosk mode settings to a stage.
     */
    public void applyToStage(Stage stage) {
        if (!isKioskModeEnabled()) {
            return;
        }

        KioskConfig config = getConfig();

        Platform.runLater(() -> {
            // Full screen mode
            if (Boolean.TRUE.equals(config.getFullScreen())) {
                stage.setMaximized(true);
                stage.setFullScreen(true);
                stage.setFullScreenExitHint("");
            }

            // Prevent closing (requires admin intervention)
            stage.setOnCloseRequest(event -> {
                if (isKioskModeEnabled()) {
                    event.consume();
                    log.info("Close attempt blocked in kiosk mode");
                }
            });

            // Prevent minimize if configured
            if (!Boolean.TRUE.equals(config.getAllowMinimize())) {
                stage.iconifiedProperty().addListener((obs, wasMinimized, isMinimized) -> {
                    if (isMinimized && isKioskModeEnabled()) {
                        Platform.runLater(() -> stage.setIconified(false));
                    }
                });
            }

            log.info("Kiosk mode settings applied to stage");
        });
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
