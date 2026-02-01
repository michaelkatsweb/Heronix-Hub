package com.heronixedu.hub.service;

import com.heronixedu.hub.model.ThirdPartyApp;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.InstallerType;
import com.heronixedu.hub.repository.ThirdPartyAppRepository;
import javafx.concurrent.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Service for installing third-party applications.
 * Handles downloading, verification, and silent installation of various installer types.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThirdPartyInstallerService {

    private final ThirdPartyAppRepository appRepository;
    private final NetworkConfigService networkConfigService;
    private final AuditLogService auditLogService;
    private final DigitalSignatureService signatureService;
    private final DownloadSecurityService downloadSecurityService;
    private final VirusScanService virusScanService;

    private static final String INSTALL_BASE_PATH = System.getProperty("user.home") +
            File.separator + ".heronix" + File.separator + "third-party";
    private static final String DOWNLOAD_TEMP_PATH = System.getProperty("user.home") +
            File.separator + ".heronix" + File.separator + "downloads";
    private static final int INSTALL_TIMEOUT_MINUTES = 30;

    /**
     * Creates an installation task for a third-party application.
     */
    public Task<InstallationResult> installApp(ThirdPartyApp app, User user, Consumer<Double> progressCallback) {
        return new Task<>() {
            @Override
            protected InstallationResult call() throws Exception {
                log.info("Starting installation of {} for user {}", app.getAppName(), user.getUsername());

                try {
                    // Validate app is approved
                    if (!app.getIsApproved()) {
                        throw new SecurityException("Application is not approved for installation: " + app.getAppName());
                    }

                    // Validate download source against security policies
                    DownloadSecurityService.SecurityCheckResult sourceCheck =
                            downloadSecurityService.validateAppDownloadSource(app);
                    if (!sourceCheck.isAllowed()) {
                        auditLogService.log(
                                com.heronixedu.hub.model.enums.AuditAction.DOWNLOAD_SOURCE_BLOCKED,
                                user.getUsername(),
                                "Blocked installation of " + app.getAppName() + ": " + sourceCheck.reason()
                        );
                        throw new SecurityException("Download source blocked: " + sourceCheck.reason());
                    }

                    updateProgress(0, 100);
                    progressCallback.accept(0.0);

                    // Step 1: Prepare directories (5%)
                    prepareDirectories();
                    updateProgress(5, 100);
                    progressCallback.accept(0.05);

                    // Step 2: Download installer (5% - 60%)
                    Path installerPath = downloadInstaller(app, (downloaded, total) -> {
                        double progress = 5 + (downloaded * 55.0 / Math.max(total, 1));
                        updateProgress((long) progress, 100);
                        progressCallback.accept(progress / 100.0);
                    });
                    updateProgress(60, 100);
                    progressCallback.accept(0.60);

                    // Step 3: Verify checksum (60% - 65%)
                    if (app.getChecksumSha256() != null && !app.getChecksumSha256().isEmpty()) {
                        if (!verifyChecksum(installerPath, app.getChecksumSha256())) {
                            Files.deleteIfExists(installerPath);
                            throw new SecurityException("Checksum verification failed for " + app.getAppName());
                        }
                        log.info("Checksum verified for {}", app.getAppName());
                    }
                    updateProgress(65, 100);
                    progressCallback.accept(0.65);

                    // Step 4: Verify digital signature (65% - 70%)
                    if (Boolean.TRUE.equals(app.getRequireSignature())) {
                        DigitalSignatureService.SignatureVerificationResult sigResult =
                                signatureService.verifySignature(installerPath);

                        if (!sigResult.isSigned()) {
                            Files.deleteIfExists(installerPath);
                            auditLogService.log(
                                    com.heronixedu.hub.model.enums.AuditAction.SIGNATURE_VERIFICATION_FAILED,
                                    user.getUsername(),
                                    "Unsigned installer rejected: " + app.getAppName()
                            );
                            throw new SecurityException("Installer is not digitally signed: " + app.getAppName());
                        }

                        if (!sigResult.isValid()) {
                            Files.deleteIfExists(installerPath);
                            auditLogService.log(
                                    com.heronixedu.hub.model.enums.AuditAction.SIGNATURE_VERIFICATION_FAILED,
                                    user.getUsername(),
                                    "Invalid signature for " + app.getAppName() + ": " + sigResult.errorMessage()
                            );
                            throw new SecurityException("Invalid digital signature: " + sigResult.errorMessage());
                        }

                        // Verify publisher if specified
                        if (app.getExpectedPublisher() != null && !app.getExpectedPublisher().isEmpty()) {
                            if (!signatureService.verifyPublisher(sigResult, app.getExpectedPublisher())) {
                                Files.deleteIfExists(installerPath);
                                auditLogService.log(
                                        com.heronixedu.hub.model.enums.AuditAction.SIGNATURE_VERIFICATION_FAILED,
                                        user.getUsername(),
                                        "Publisher mismatch for " + app.getAppName() +
                                                ": expected '" + app.getExpectedPublisher() +
                                                "', got '" + sigResult.signerName() + "'"
                                );
                                throw new SecurityException("Publisher verification failed: expected '" +
                                        app.getExpectedPublisher() + "', got '" + sigResult.signerName() + "'");
                            }
                        }

                        // Verify thumbprint if specified (certificate pinning)
                        if (app.getExpectedCertThumbprint() != null && !app.getExpectedCertThumbprint().isEmpty()) {
                            if (!signatureService.verifyThumbprint(sigResult, app.getExpectedCertThumbprint())) {
                                Files.deleteIfExists(installerPath);
                                auditLogService.log(
                                        com.heronixedu.hub.model.enums.AuditAction.SIGNATURE_VERIFICATION_FAILED,
                                        user.getUsername(),
                                        "Certificate thumbprint mismatch for " + app.getAppName()
                                );
                                throw new SecurityException("Certificate thumbprint verification failed");
                            }
                        }

                        // Record successful verification
                        app.recordSignatureVerification(sigResult.signerName());
                        log.info("Digital signature verified for {}: signed by {}",
                                app.getAppName(), sigResult.signerName());
                    }
                    updateProgress(70, 100);
                    progressCallback.accept(0.70);

                    // Step 5: Virus scan (70% - 75%)
                    VirusScanService.ScanResult scanResult = virusScanService.scanFile(installerPath);
                    if (scanResult.isThreatDetected()) {
                        Files.deleteIfExists(installerPath);
                        auditLogService.log(
                                com.heronixedu.hub.model.enums.AuditAction.VIRUS_SCAN_FAILED,
                                user.getUsername(),
                                "Malware detected in " + app.getAppName() + ": " + scanResult.threatName()
                        );
                        throw new SecurityException("Malware detected: " + scanResult.threatName());
                    }
                    if (!scanResult.scanCompleted() && scanResult.errorMessage() != null) {
                        log.warn("Virus scan incomplete for {}: {}", app.getAppName(), scanResult.errorMessage());
                        // Don't block on scan errors, but log warning
                    } else if (scanResult.isSafe()) {
                        log.info("Virus scan clean for {} (scanned by {})",
                                app.getAppName(), scanResult.scannerUsed());
                    }
                    updateProgress(75, 100);
                    progressCallback.accept(0.75);

                    // Step 6: Execute installer (75% - 95%)
                    executeInstaller(app, installerPath, (installerProgress) -> {
                        double progress = 70 + (installerProgress * 25);
                        updateProgress((long) progress, 100);
                        progressCallback.accept(progress / 100.0);
                    });
                    updateProgress(95, 100);
                    progressCallback.accept(0.95);

                    // Step 5: Update database and cleanup (95% - 100%)
                    app.setIsInstalled(true);
                    app.setInstalledAt(LocalDateTime.now());
                    app.setCurrentVersion(app.getLatestVersion());
                    appRepository.save(app);

                    // Cleanup downloaded installer
                    Files.deleteIfExists(installerPath);

                    updateProgress(100, 100);
                    progressCallback.accept(1.0);

                    auditLogService.logThirdPartyInstall(user, app, true,
                            "Successfully installed " + app.getAppName() + " v" + app.getCurrentVersion());

                    log.info("Successfully installed {} for user {}", app.getAppName(), user.getUsername());
                    return new InstallationResult(true, app, null);

                } catch (Exception e) {
                    log.error("Installation failed for {}: {}", app.getAppName(), e.getMessage(), e);
                    auditLogService.logThirdPartyInstall(user, app, false, e.getMessage());
                    return new InstallationResult(false, app, e.getMessage());
                }
            }
        };
    }

    /**
     * Uninstalls a third-party application.
     */
    public Task<InstallationResult> uninstallApp(ThirdPartyApp app, User user, Consumer<Double> progressCallback) {
        return new Task<>() {
            @Override
            protected InstallationResult call() throws Exception {
                log.info("Starting uninstallation of {} for user {}", app.getAppName(), user.getUsername());

                try {
                    updateProgress(0, 100);
                    progressCallback.accept(0.0);

                    if (app.getUninstallCommand() != null && !app.getUninstallCommand().isEmpty()) {
                        // Execute uninstall command
                        updateProgress(20, 100);
                        progressCallback.accept(0.20);

                        ProcessBuilder pb = createUninstallProcess(app);
                        Process process = pb.start();

                        boolean completed = process.waitFor(INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                        if (!completed) {
                            process.destroyForcibly();
                            throw new RuntimeException("Uninstall process timed out");
                        }

                        int exitCode = process.exitValue();
                        if (exitCode != 0) {
                            log.warn("Uninstaller exited with code {} for {}", exitCode, app.getAppName());
                        }
                    }

                    updateProgress(80, 100);
                    progressCallback.accept(0.80);

                    // Update database
                    app.setIsInstalled(false);
                    app.setInstalledAt(null);
                    appRepository.save(app);

                    updateProgress(100, 100);
                    progressCallback.accept(1.0);

                    auditLogService.logThirdPartyUninstall(user, app, true,
                            "Successfully uninstalled " + app.getAppName());

                    log.info("Successfully uninstalled {} for user {}", app.getAppName(), user.getUsername());
                    return new InstallationResult(true, app, null);

                } catch (Exception e) {
                    log.error("Uninstallation failed for {}: {}", app.getAppName(), e.getMessage(), e);
                    auditLogService.logThirdPartyUninstall(user, app, false, e.getMessage());
                    return new InstallationResult(false, app, e.getMessage());
                }
            }
        };
    }

    private void prepareDirectories() throws IOException {
        Files.createDirectories(Paths.get(INSTALL_BASE_PATH));
        Files.createDirectories(Paths.get(DOWNLOAD_TEMP_PATH));
    }

    private Path downloadInstaller(ThirdPartyApp app, ProgressCallback progressCallback) throws Exception {
        String downloadSource = app.getLocalPath() != null ? app.getLocalPath() : app.getDownloadUrl();

        if (downloadSource == null || downloadSource.isEmpty()) {
            throw new IllegalStateException("No download source configured for " + app.getAppName());
        }

        String fileName = app.getAppCode().toLowerCase() + getFileExtension(app.getInstallerType());
        Path targetPath = Paths.get(DOWNLOAD_TEMP_PATH, fileName);

        if (downloadSource.startsWith("http://") || downloadSource.startsWith("https://")) {
            downloadFromHttp(downloadSource, targetPath, progressCallback);
        } else {
            copyFromLocal(downloadSource, targetPath, progressCallback);
        }

        return targetPath;
    }

    private String getFileExtension(InstallerType type) {
        return switch (type) {
            case EXE -> ".exe";
            case MSI -> ".msi";
            case MSIX -> ".msix";
            case ZIP -> ".zip";
            default -> "";
        };
    }

    private void downloadFromHttp(String url, Path destination, ProgressCallback progressCallback) throws Exception {
        var config = networkConfigService.getActiveConfig();
        var uri = java.net.URI.create(url);
        java.net.HttpURLConnection connection;

        if (config.getProxyEnabled() && config.getProxyHost() != null) {
            connection = (java.net.HttpURLConnection) uri.toURL().openConnection(
                    networkConfigService.getConfiguredProxy(config));
        } else {
            connection = (java.net.HttpURLConnection) uri.toURL().openConnection();
        }

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", "Heronix-Hub/1.0");

        long totalSize = connection.getContentLengthLong();
        long downloaded = 0;

        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(destination)) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;
                progressCallback.onProgress(downloaded, totalSize);
            }
        } finally {
            connection.disconnect();
        }
    }

    private void copyFromLocal(String source, Path destination, ProgressCallback progressCallback) throws Exception {
        Path sourcePath = Paths.get(source);

        if (!Files.exists(sourcePath)) {
            throw new FileNotFoundException("Local installer not found: " + source);
        }

        long totalSize = Files.size(sourcePath);
        long copied = 0;

        try (InputStream in = Files.newInputStream(sourcePath);
             OutputStream out = Files.newOutputStream(destination)) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                copied += bytesRead;
                progressCallback.onProgress(copied, totalSize);
            }
        }
    }

    private boolean verifyChecksum(Path file, String expectedChecksum) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        byte[] hashBytes = digest.digest();
        String actualChecksum = bytesToHex(hashBytes);

        return actualChecksum.equalsIgnoreCase(expectedChecksum);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void executeInstaller(ThirdPartyApp app, Path installerPath, Consumer<Double> progressCallback)
            throws Exception {

        ProcessBuilder pb = createInstallProcess(app, installerPath);

        log.info("Executing installer: {}", String.join(" ", pb.command()));

        Process process = pb.start();

        // Read output streams to prevent blocking
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("Installer output: {}", line);
                }
            } catch (IOException e) {
                log.warn("Error reading installer output", e);
            }
        });
        outputReader.start();

        Thread errorReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.warn("Installer error: {}", line);
                }
            } catch (IOException e) {
                log.warn("Error reading installer error stream", e);
            }
        });
        errorReader.start();

        // Wait for completion with timeout
        boolean completed = process.waitFor(INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        outputReader.join(5000);
        errorReader.join(5000);

        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("Installation process timed out after " + INSTALL_TIMEOUT_MINUTES + " minutes");
        }

        int exitCode = process.exitValue();

        // Common success exit codes
        if (exitCode != 0 && exitCode != 3010) { // 3010 = reboot required
            throw new RuntimeException("Installer exited with error code: " + exitCode);
        }

        if (exitCode == 3010) {
            log.info("Installation completed but requires restart");
        }

        progressCallback.accept(1.0);
    }

    private ProcessBuilder createInstallProcess(ThirdPartyApp app, Path installerPath) {
        ProcessBuilder pb;
        String installerPathStr = installerPath.toAbsolutePath().toString();

        switch (app.getInstallerType()) {
            case MSI -> {
                // Use msiexec for MSI packages
                String args = app.getSilentInstallArgs() != null ?
                        app.getSilentInstallArgs() : "/qn /norestart";
                pb = new ProcessBuilder("msiexec", "/i", installerPathStr);
                for (String arg : args.split("\\s+")) {
                    if (!arg.isEmpty()) {
                        pb.command().add(arg);
                    }
                }
            }
            case EXE -> {
                // Execute directly with silent args
                pb = new ProcessBuilder(installerPathStr);
                String args = app.getSilentInstallArgs();
                if (args != null && !args.isEmpty()) {
                    for (String arg : args.split("\\s+")) {
                        if (!arg.isEmpty()) {
                            pb.command().add(arg);
                        }
                    }
                }
            }
            case ZIP -> {
                // Extract ZIP to install path
                String installPath = app.getInstallPath() != null ?
                        app.getInstallPath() : INSTALL_BASE_PATH + File.separator + app.getAppCode().toLowerCase();
                pb = new ProcessBuilder("powershell", "-Command",
                        "Expand-Archive", "-Path", installerPathStr,
                        "-DestinationPath", installPath, "-Force");
            }
            case WINGET -> {
                pb = new ProcessBuilder("winget", "install", "--id", app.getAppCode(),
                        "--silent", "--accept-package-agreements", "--accept-source-agreements");
            }
            case CHOCOLATEY -> {
                pb = new ProcessBuilder("choco", "install", app.getAppCode(), "-y", "--no-progress");
            }
            default -> {
                pb = new ProcessBuilder(installerPathStr);
            }
        }

        pb.redirectErrorStream(false);
        return pb;
    }

    private ProcessBuilder createUninstallProcess(ThirdPartyApp app) {
        String uninstallCmd = app.getUninstallCommand();

        if (uninstallCmd.startsWith("msiexec")) {
            // Parse msiexec command
            return new ProcessBuilder("cmd", "/c", uninstallCmd);
        } else if (uninstallCmd.contains(" ")) {
            // Command with arguments
            return new ProcessBuilder("cmd", "/c", uninstallCmd);
        } else {
            // Simple executable path
            return new ProcessBuilder(uninstallCmd);
        }
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(long completed, long total);
    }

    public static class InstallationResult {
        private final boolean success;
        private final ThirdPartyApp app;
        private final String errorMessage;

        public InstallationResult(boolean success, ThirdPartyApp app, String errorMessage) {
            this.success = success;
            this.app = app;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public ThirdPartyApp getApp() { return app; }
        public String getErrorMessage() { return errorMessage; }
    }
}
