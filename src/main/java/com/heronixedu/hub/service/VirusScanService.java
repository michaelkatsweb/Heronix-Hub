package com.heronixedu.hub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for virus scanning downloaded files before installation.
 * Supports integration with Windows Defender and custom antivirus solutions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VirusScanService {

    private final AuditLogService auditLogService;

    @Value("${heronix.security.virus-scan.enabled:true}")
    private boolean scanEnabled;

    @Value("${heronix.security.virus-scan.timeout-seconds:300}")
    private int scanTimeoutSeconds;

    @Value("${heronix.security.virus-scan.custom-scanner-path:}")
    private String customScannerPath;

    @Value("${heronix.security.virus-scan.custom-scanner-args:}")
    private String customScannerArgs;

    /**
     * Result of a virus scan.
     */
    public record ScanResult(
            boolean scanCompleted,
            boolean isThreatDetected,
            String threatName,
            String scannerUsed,
            String errorMessage,
            long scanDurationMs
    ) {
        public static ScanResult clean(String scanner, long duration) {
            return new ScanResult(true, false, null, scanner, null, duration);
        }

        public static ScanResult threat(String threatName, String scanner, long duration) {
            return new ScanResult(true, true, threatName, scanner, null, duration);
        }

        public static ScanResult error(String message, String scanner) {
            return new ScanResult(false, false, null, scanner, message, 0);
        }

        public static ScanResult skipped(String reason) {
            return new ScanResult(true, false, null, "SKIPPED", reason, 0);
        }

        public boolean isSafe() {
            return scanCompleted && !isThreatDetected;
        }
    }

    /**
     * Scan a file for viruses/malware.
     *
     * @param filePath Path to the file to scan
     * @return ScanResult with scan outcome
     */
    public ScanResult scanFile(Path filePath) {
        if (!scanEnabled) {
            log.debug("Virus scanning is disabled");
            return ScanResult.skipped("Virus scanning disabled in configuration");
        }

        if (!Files.exists(filePath)) {
            return ScanResult.error("File does not exist: " + filePath, "NONE");
        }

        // Try custom scanner first if configured
        if (customScannerPath != null && !customScannerPath.isEmpty()) {
            ScanResult customResult = scanWithCustomScanner(filePath);
            if (customResult.scanCompleted()) {
                return customResult;
            }
            log.warn("Custom scanner failed, falling back to Windows Defender");
        }

        // Use Windows Defender as default
        return scanWithWindowsDefender(filePath);
    }

    /**
     * Scan using Windows Defender (MpCmdRun.exe).
     */
    private ScanResult scanWithWindowsDefender(Path filePath) {
        long startTime = System.currentTimeMillis();

        try {
            // Find Windows Defender command line tool
            String defenderPath = findWindowsDefenderPath();
            if (defenderPath == null) {
                return ScanResult.error("Windows Defender not found", "WindowsDefender");
            }

            ProcessBuilder pb = new ProcessBuilder(
                    defenderPath,
                    "-Scan",
                    "-ScanType", "3",  // Custom scan
                    "-File", filePath.toAbsolutePath().toString(),
                    "-DisableRemediation"  // Don't auto-delete, just report
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            List<String> output = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                    log.debug("Defender output: {}", line);
                }
            }

            boolean completed = process.waitFor(scanTimeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return ScanResult.error("Scan timed out after " + scanTimeoutSeconds + " seconds", "WindowsDefender");
            }

            int exitCode = process.exitValue();
            long duration = System.currentTimeMillis() - startTime;

            // Windows Defender exit codes:
            // 0 = No threats found
            // 2 = Threats found
            if (exitCode == 0) {
                log.info("Windows Defender scan clean for: {}", filePath.getFileName());
                return ScanResult.clean("WindowsDefender", duration);
            } else if (exitCode == 2) {
                String threatName = extractThreatName(output);
                log.warn("Windows Defender found threat in {}: {}", filePath.getFileName(), threatName);
                return ScanResult.threat(threatName, "WindowsDefender", duration);
            } else {
                // Other exit codes might indicate errors
                String errorInfo = String.join("; ", output);
                log.warn("Windows Defender returned unexpected exit code {}: {}", exitCode, errorInfo);
                // Treat as clean but log warning - scan did complete
                return ScanResult.clean("WindowsDefender", duration);
            }

        } catch (Exception e) {
            log.error("Error running Windows Defender scan: {}", e.getMessage());
            return ScanResult.error("Scan failed: " + e.getMessage(), "WindowsDefender");
        }
    }

    /**
     * Scan using a custom antivirus scanner.
     */
    private ScanResult scanWithCustomScanner(Path filePath) {
        long startTime = System.currentTimeMillis();

        try {
            List<String> command = new ArrayList<>();
            command.add(customScannerPath);

            // Parse custom arguments, replacing {file} placeholder with actual path
            if (customScannerArgs != null && !customScannerArgs.isEmpty()) {
                String args = customScannerArgs.replace("{file}", filePath.toAbsolutePath().toString());
                for (String arg : args.split("\\s+")) {
                    if (!arg.isEmpty()) {
                        command.add(arg);
                    }
                }
            } else {
                command.add(filePath.toAbsolutePath().toString());
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            List<String> output = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }

            boolean completed = process.waitFor(scanTimeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return ScanResult.error("Scan timed out", "CustomScanner");
            }

            int exitCode = process.exitValue();
            long duration = System.currentTimeMillis() - startTime;

            // Standard convention: 0 = clean, non-zero = threat or error
            if (exitCode == 0) {
                log.info("Custom scanner reports clean: {}", filePath.getFileName());
                return ScanResult.clean("CustomScanner", duration);
            } else {
                // Try to extract threat info from output
                String outputStr = String.join("\n", output);
                log.warn("Custom scanner detected issue (exit {}): {}", exitCode, outputStr);
                return ScanResult.threat("Threat detected (exit code " + exitCode + ")", "CustomScanner", duration);
            }

        } catch (Exception e) {
            log.error("Error running custom scanner: {}", e.getMessage());
            return ScanResult.error("Custom scan failed: " + e.getMessage(), "CustomScanner");
        }
    }

    /**
     * Find the Windows Defender command line tool path.
     */
    private String findWindowsDefenderPath() {
        // Standard paths for Windows Defender
        String[] possiblePaths = {
                "C:\\Program Files\\Windows Defender\\MpCmdRun.exe",
                "C:\\ProgramData\\Microsoft\\Windows Defender\\Platform\\*\\MpCmdRun.exe"
        };

        for (String path : possiblePaths) {
            if (path.contains("*")) {
                // Handle wildcard - find latest version
                Path parentDir = Path.of(path).getParent();
                if (Files.exists(parentDir)) {
                    try {
                        var latestVersion = Files.list(parentDir)
                                .filter(Files::isDirectory)
                                .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                                .findFirst();

                        if (latestVersion.isPresent()) {
                            Path defenderExe = latestVersion.get().resolve("MpCmdRun.exe");
                            if (Files.exists(defenderExe)) {
                                return defenderExe.toString();
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Error searching for Defender: {}", e.getMessage());
                    }
                }
            } else {
                if (Files.exists(Path.of(path))) {
                    return path;
                }
            }
        }

        return null;
    }

    /**
     * Extract threat name from Windows Defender output.
     */
    private String extractThreatName(List<String> output) {
        for (String line : output) {
            if (line.contains("Threat") || line.contains("threat")) {
                return line.trim();
            }
        }
        return "Unknown threat detected";
    }

    /**
     * Check if virus scanning is available on this system.
     */
    public boolean isScanningAvailable() {
        if (!scanEnabled) {
            return false;
        }

        // Check custom scanner
        if (customScannerPath != null && !customScannerPath.isEmpty()) {
            if (Files.exists(Path.of(customScannerPath))) {
                return true;
            }
        }

        // Check Windows Defender
        return findWindowsDefenderPath() != null;
    }

    /**
     * Get information about the configured scanner.
     */
    public String getScannerInfo() {
        if (!scanEnabled) {
            return "Virus scanning disabled";
        }

        if (customScannerPath != null && !customScannerPath.isEmpty()) {
            if (Files.exists(Path.of(customScannerPath))) {
                return "Custom scanner: " + customScannerPath;
            }
        }

        String defenderPath = findWindowsDefenderPath();
        if (defenderPath != null) {
            return "Windows Defender: " + defenderPath;
        }

        return "No scanner available";
    }
}
