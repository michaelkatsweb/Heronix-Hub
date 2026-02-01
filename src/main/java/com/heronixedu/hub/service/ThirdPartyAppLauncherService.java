package com.heronixedu.hub.service;

import com.heronixedu.hub.model.ThirdPartyApp;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.AuditAction;
import com.heronixedu.hub.repository.ThirdPartyAppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for launching installed third-party applications.
 * Handles finding executables, launching processes, and tracking usage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThirdPartyAppLauncherService {

    private final ThirdPartyAppRepository appRepository;
    private final AuditLogService auditLogService;

    // Common installation paths to search for executables
    private static final String[] COMMON_INSTALL_PATHS = {
            System.getenv("ProgramFiles"),
            System.getenv("ProgramFiles(x86)"),
            System.getenv("LocalAppData") + "\\Programs",
            System.getProperty("user.home") + "\\.heronix\\third-party"
    };

    /**
     * Launch a third-party application.
     *
     * @param app  The application to launch
     * @param user The user launching the application
     * @throws IOException if the application cannot be launched
     */
    public void launchApp(ThirdPartyApp app, User user) throws IOException {
        log.info("Launching application: {} for user: {}", app.getAppName(), user.getUsername());

        if (!Boolean.TRUE.equals(app.getIsInstalled())) {
            throw new IllegalStateException("Application is not installed: " + app.getAppName());
        }

        // Find the executable
        String executablePath = findExecutable(app);
        if (executablePath == null) {
            throw new IOException("Could not find executable for " + app.getAppName());
        }

        // Launch the application
        ProcessBuilder pb = new ProcessBuilder(executablePath);
        pb.directory(new File(executablePath).getParentFile());
        pb.redirectErrorStream(true);

        // Don't wait for the process - let it run independently
        Process process = pb.start();

        // Update last launched timestamp
        app.setLastUpdated(LocalDateTime.now());
        appRepository.save(app);

        // Log the launch
        auditLogService.log(
                AuditAction.PRODUCT_LAUNCH,
                user.getUsername(),
                "Launched third-party application: " + app.getAppName()
        );

        log.info("Successfully launched {} (PID: {})", app.getAppName(),
                process.pid());
    }

    /**
     * Find the executable path for an application.
     */
    private String findExecutable(ThirdPartyApp app) {
        // First, check if executable name is specified and install path is known
        if (app.getExecutableName() != null && !app.getExecutableName().isEmpty()) {
            // Check specified install path
            if (app.getInstallPath() != null && !app.getInstallPath().isEmpty()) {
                Path exePath = Paths.get(app.getInstallPath(), app.getExecutableName());
                if (Files.exists(exePath) && Files.isExecutable(exePath)) {
                    return exePath.toString();
                }
            }

            // Search common installation paths
            for (String basePath : COMMON_INSTALL_PATHS) {
                if (basePath == null) continue;

                // Try direct path
                Path direct = Paths.get(basePath, app.getExecutableName());
                if (Files.exists(direct) && Files.isExecutable(direct)) {
                    return direct.toString();
                }

                // Try with app name as subdirectory
                Path withSubdir = Paths.get(basePath, app.getAppName(), app.getExecutableName());
                if (Files.exists(withSubdir) && Files.isExecutable(withSubdir)) {
                    return withSubdir.toString();
                }

                // Try with publisher/app name as subdirectory
                if (app.getPublisher() != null) {
                    Path withPublisher = Paths.get(basePath, app.getPublisher(), app.getAppName(), app.getExecutableName());
                    if (Files.exists(withPublisher) && Files.isExecutable(withPublisher)) {
                        return withPublisher.toString();
                    }
                }
            }
        }

        // Try to find by searching for common executable patterns
        return searchForExecutable(app);
    }

    /**
     * Search for an executable based on the app name.
     */
    private String searchForExecutable(ThirdPartyApp app) {
        List<String> possibleNames = generatePossibleExecutableNames(app);

        for (String basePath : COMMON_INSTALL_PATHS) {
            if (basePath == null) continue;

            for (String exeName : possibleNames) {
                // Direct search
                Path direct = Paths.get(basePath, exeName);
                if (Files.exists(direct)) {
                    return direct.toString();
                }

                // Search in subdirectories (one level deep)
                try {
                    File baseDir = new File(basePath);
                    if (baseDir.exists() && baseDir.isDirectory()) {
                        File[] subdirs = baseDir.listFiles(File::isDirectory);
                        if (subdirs != null) {
                            for (File subdir : subdirs) {
                                // Check if subdir name matches app name (case insensitive)
                                if (subdir.getName().toLowerCase().contains(
                                        app.getAppName().toLowerCase().replace(" ", ""))) {
                                    Path inSubdir = Paths.get(subdir.getAbsolutePath(), exeName);
                                    if (Files.exists(inSubdir)) {
                                        return inSubdir.toString();
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error searching in {}: {}", basePath, e.getMessage());
                }
            }
        }

        return null;
    }

    /**
     * Generate possible executable names based on the app.
     */
    private List<String> generatePossibleExecutableNames(ThirdPartyApp app) {
        List<String> names = new ArrayList<>();
        String appName = app.getAppName();
        String appCode = app.getAppCode();

        // Add specified executable name first
        if (app.getExecutableName() != null && !app.getExecutableName().isEmpty()) {
            names.add(app.getExecutableName());
        }

        // Common variations
        names.add(appName + ".exe");
        names.add(appName.replace(" ", "") + ".exe");
        names.add(appName.replace(" ", "-") + ".exe");
        names.add(appName.replace(" ", "_") + ".exe");
        names.add(appName.toLowerCase() + ".exe");
        names.add(appName.toLowerCase().replace(" ", "") + ".exe");

        if (appCode != null) {
            names.add(appCode + ".exe");
            names.add(appCode.toLowerCase() + ".exe");
        }

        return names;
    }

    /**
     * Check if an application's executable can be found.
     */
    public boolean canLaunch(ThirdPartyApp app) {
        if (!Boolean.TRUE.equals(app.getIsInstalled())) {
            return false;
        }
        return findExecutable(app) != null;
    }

    /**
     * Get the executable path for an application (for display purposes).
     */
    public String getExecutablePath(ThirdPartyApp app) {
        return findExecutable(app);
    }

    /**
     * Update the executable name for an app after finding it.
     */
    public void updateExecutablePath(ThirdPartyApp app) {
        String path = findExecutable(app);
        if (path != null) {
            File exe = new File(path);
            app.setExecutableName(exe.getName());
            app.setInstallPath(exe.getParent());
            appRepository.save(app);
            log.info("Updated executable path for {}: {}", app.getAppName(), path);
        }
    }
}
