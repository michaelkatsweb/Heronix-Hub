package com.heronixedu.hub.service;

import com.heronixedu.hub.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background service that monitors classroom session status for students.
 * Handles screen freezing, app restrictions, and attention alerts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentSessionMonitorService {

    private final ClassroomControlService classroomControlService;
    private final ScreenFreezeService screenFreezeService;

    private ScheduledExecutorService scheduler;
    private User currentStudent;
    private boolean isMonitoring = false;
    private String lastFreezeState = "unfrozen";
    private String lastPrivateMessage = null;
    private boolean lastAttentionState = false;

    /**
     * Start monitoring for a student.
     */
    public void startMonitoring(User student) {
        if (isMonitoring) {
            stopMonitoring();
        }

        this.currentStudent = student;
        this.isMonitoring = true;

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkSessionStatus, 0, 2, TimeUnit.SECONDS);

        log.info("Started session monitoring for student: {}", student.getUsername());
    }

    /**
     * Stop monitoring.
     */
    public void stopMonitoring() {
        isMonitoring = false;

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Ensure screen is unfrozen when stopping
        screenFreezeService.unfreezeScreen();

        log.info("Stopped session monitoring");
    }

    /**
     * Check session status and apply restrictions.
     */
    private void checkSessionStatus() {
        if (!isMonitoring || currentStudent == null) {
            return;
        }

        try {
            // Send heartbeat and get current status
            var statusOpt = classroomControlService.getStudentSessionStatus(currentStudent);

            if (statusOpt.isEmpty()) {
                // Not in a session - ensure unfrozen
                if (screenFreezeService.isFrozen()) {
                    screenFreezeService.unfreezeScreen();
                    lastFreezeState = "unfrozen";
                }
                return;
            }

            var status = statusOpt.get();

            // Send heartbeat
            try {
                String currentApp = getCurrentActiveApp();
                classroomControlService.heartbeat(currentStudent, currentApp);
            } catch (Exception e) {
                log.debug("Heartbeat failed: {}", e.getMessage());
            }

            // Handle screen freeze
            if (status.screenFrozen() && !"frozen".equals(lastFreezeState)) {
                screenFreezeService.freezeScreen(status.freezeMessage(), status.teacherName());
                lastFreezeState = "frozen";
                log.info("Screen frozen by teacher: {}", status.teacherName());
            } else if (!status.screenFrozen() && "frozen".equals(lastFreezeState)) {
                screenFreezeService.unfreezeScreen();
                lastFreezeState = "unfrozen";
                log.info("Screen unfrozen by teacher");
            }

            // Handle attention request
            if (status.attentionRequested() && !lastAttentionState) {
                screenFreezeService.showAttentionAlert();
                classroomControlService.clearAttentionRequest(currentStudent);
                lastAttentionState = true;
            } else if (!status.attentionRequested()) {
                lastAttentionState = false;
            }

            // Handle private message
            if (status.privateMessage() != null && !status.privateMessage().equals(lastPrivateMessage)) {
                screenFreezeService.showPrivateMessage(status.privateMessage(), status.teacherName());
                classroomControlService.clearPrivateMessage(currentStudent);
                lastPrivateMessage = status.privateMessage();
            } else if (status.privateMessage() == null) {
                lastPrivateMessage = null;
            }

        } catch (Exception e) {
            log.debug("Error checking session status: {}", e.getMessage());
        }
    }

    /**
     * Get the currently active application name (simplified implementation).
     */
    private String getCurrentActiveApp() {
        // This would need native integration to get the actual focused window
        // For now, return a placeholder
        return "Heronix Hub";
    }

    /**
     * Check if the student is currently in an active session.
     */
    public boolean isInActiveSession() {
        if (currentStudent == null) {
            return false;
        }
        return classroomControlService.getStudentSessionStatus(currentStudent).isPresent();
    }

    /**
     * Get current session info for display.
     */
    public ClassroomControlService.StudentSessionStatus getCurrentSessionStatus() {
        if (currentStudent == null) {
            return null;
        }
        return classroomControlService.getStudentSessionStatus(currentStudent).orElse(null);
    }

    /**
     * Check if an app is allowed to launch.
     */
    public boolean isAppAllowed(String appCode) {
        if (currentStudent == null) {
            return true;
        }

        var status = classroomControlService.getStudentSessionStatus(currentStudent);
        if (status.isEmpty()) {
            return true;
        }

        var sessionStatus = status.get();
        if (!sessionStatus.appsRestricted()) {
            return true;
        }

        String allowedApps = sessionStatus.allowedApps();
        if (allowedApps == null || allowedApps.isEmpty()) {
            return false; // Restricted but no apps allowed
        }

        return allowedApps.toLowerCase().contains(appCode.toLowerCase());
    }
}
