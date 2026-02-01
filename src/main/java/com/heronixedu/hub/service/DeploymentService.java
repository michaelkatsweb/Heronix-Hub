package com.heronixedu.hub.service;

import com.heronixedu.hub.model.InstallationLog;
import com.heronixedu.hub.model.NetworkConfig;
import com.heronixedu.hub.model.Product;
import com.heronixedu.hub.model.ProductVersion;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.ServerType;
import com.heronixedu.hub.repository.InstallationLogRepository;
import com.heronixedu.hub.repository.ProductRepository;
import com.heronixedu.hub.repository.ProductVersionRepository;
import javafx.concurrent.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeploymentService {

    private final ProductRepository productRepository;
    private final ProductVersionRepository productVersionRepository;
    private final InstallationLogRepository installationLogRepository;
    private final NetworkConfigService networkConfigService;
    private final AuditLogService auditLogService;

    public VersionCheckResult checkForUpdates(Product product) {
        VersionCheckResult result = new VersionCheckResult();
        result.setCurrentVersion(product.getCurrentVersion());

        ProductVersion latestVersion = productVersionRepository
                .findByProductIdAndIsLatestTrue(product.getId())
                .orElse(null);

        if (latestVersion != null) {
            result.setLatestVersion(latestVersion.getVersion());
            result.setUpdateAvailable(!latestVersion.getVersion().equals(product.getCurrentVersion()));
            result.setChangelog(latestVersion.getChangelog());
            result.setFileSize(latestVersion.getFileSize());
            result.setDownloadUrl(latestVersion.getDownloadUrl());
        } else {
            result.setLatestVersion(product.getCurrentVersion());
            result.setUpdateAvailable(false);
        }

        return result;
    }

    public Task<InstallationResult> installProduct(Product product, User user, Consumer<Double> progressCallback) {
        return new Task<>() {
            @Override
            protected InstallationResult call() throws Exception {
                InstallationLog installLog = createInstallLog(product, user, "INSTALL_START", "IN_PROGRESS");

                try {
                    updateProgress(0, 100);
                    progressCallback.accept(0.0);

                    // 1. Get network config and determine download source
                    NetworkConfig config = networkConfigService.getActiveConfig();
                    String downloadPath = resolveDownloadPath(product, config);

                    updateProgress(5, 100);
                    progressCallback.accept(0.05);

                    // 2. Download file
                    Path tempFile = downloadFile(downloadPath, config, (downloaded, total) -> {
                        double progress = 5 + (downloaded * 70.0 / Math.max(total, 1));
                        updateProgress((long) progress, 100);
                        progressCallback.accept(progress / 100.0);
                    });

                    updateProgress(75, 100);
                    progressCallback.accept(0.75);

                    // 3. Verify checksum (if available)
                    ProductVersion version = productVersionRepository
                            .findByProductIdAndIsLatestTrue(product.getId())
                            .orElse(null);

                    if (version != null && version.getChecksumSha256() != null) {
                        if (!verifyChecksum(tempFile, version.getChecksumSha256())) {
                            throw new RuntimeException("Checksum verification failed");
                        }
                    }

                    updateProgress(80, 100);
                    progressCallback.accept(0.80);

                    // 4. Extract/Install
                    Path installPath = extractAndInstall(tempFile, product);

                    updateProgress(95, 100);
                    progressCallback.accept(0.95);

                    // 5. Update database
                    product.setIsInstalled(true);
                    product.setInstallPath(installPath.toString());
                    if (version != null) {
                        product.setCurrentVersion(version.getVersion());
                    }
                    productRepository.save(product);

                    // Cleanup temp file
                    Files.deleteIfExists(tempFile);

                    updateProgress(100, 100);
                    progressCallback.accept(1.0);

                    completeInstallLog(installLog, "SUCCESS", null);
                    auditLogService.logProductInstall(user, product, true,
                            "Installed version " + product.getCurrentVersion());

                    return new InstallationResult(true, product, null);

                } catch (Exception e) {
                    log.error("Installation failed for {}: {}", product.getProductCode(), e.getMessage(), e);
                    completeInstallLog(installLog, "FAILED", e.getMessage());
                    auditLogService.logProductInstall(user, product, false, e.getMessage());
                    throw e;
                }
            }
        };
    }

    private String resolveDownloadPath(Product product, NetworkConfig config) {
        String basePath = networkConfigService.resolveServerPath(config);
        String fileName = product.getProductCode().toLowerCase() + ".zip";

        if (config.getServerType() == ServerType.CLOUD ||
                (config.getServerType() == ServerType.AUTO && !testLocalPath(config.getLocalServerPath()))) {
            // Cloud URL
            return basePath + "/products/" + fileName;
        } else {
            // Local path
            return basePath + File.separator + "products" + File.separator + fileName;
        }
    }

    private boolean testLocalPath(String path) {
        if (path == null) return false;
        File file = new File(path);
        return file.exists() && file.isDirectory();
    }

    private Path downloadFile(String source, NetworkConfig config, ProgressCallback progressCallback) throws Exception {
        Path tempFile = Files.createTempFile("heronix-download-", ".zip");

        if (source.startsWith("http://") || source.startsWith("https://")) {
            // HTTP/HTTPS download
            downloadFromHttp(source, tempFile, config, progressCallback);
        } else {
            // Local file copy
            copyFromLocal(source, tempFile, progressCallback);
        }

        return tempFile;
    }

    private void downloadFromHttp(String urlString, Path destination, NetworkConfig config,
                                   ProgressCallback progressCallback) throws Exception {
        URI uri = URI.create(urlString);
        HttpURLConnection connection;

        if (config.getProxyEnabled() && config.getProxyHost() != null) {
            connection = (HttpURLConnection) uri.toURL().openConnection(networkConfigService.getConfiguredProxy(config));
        } else {
            connection = (HttpURLConnection) uri.toURL().openConnection();
        }

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);

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

    private Path extractAndInstall(Path zipFile, Product product) throws Exception {
        String userHome = System.getProperty("user.home");
        Path installDir = Paths.get(userHome, ".heronix", "apps", product.getProductCode().toLowerCase());

        // Create install directory
        Files.createDirectories(installDir);

        // Extract ZIP
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = installDir.resolve(entry.getName()).normalize();

                // Security check - prevent zip slip
                if (!targetPath.startsWith(installDir)) {
                    throw new SecurityException("Zip entry outside target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }

                zis.closeEntry();
            }
        }

        return installDir;
    }

    private InstallationLog createInstallLog(Product product, User user, String action, String status) {
        InstallationLog log = InstallationLog.builder()
                .product(product)
                .version(product.getCurrentVersion() != null ? product.getCurrentVersion() : "1.0.0")
                .user(user)
                .action(action)
                .status(status)
                .progress(0)
                .build();
        return installationLogRepository.save(log);
    }

    private void completeInstallLog(InstallationLog installLog, String status, String errorMessage) {
        installLog.setStatus(status);
        installLog.setProgress(100);
        installLog.setCompletedAt(LocalDateTime.now());
        if (errorMessage != null) {
            installLog.setErrorMessage(errorMessage);
        }
        installationLogRepository.save(installLog);
    }

    // Callback interface for progress
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(long completed, long total);
    }

    // Result classes
    public static class VersionCheckResult {
        private String currentVersion;
        private String latestVersion;
        private boolean updateAvailable;
        private String changelog;
        private Long fileSize;
        private String downloadUrl;

        public String getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }
        public String getLatestVersion() { return latestVersion; }
        public void setLatestVersion(String latestVersion) { this.latestVersion = latestVersion; }
        public boolean isUpdateAvailable() { return updateAvailable; }
        public void setUpdateAvailable(boolean updateAvailable) { this.updateAvailable = updateAvailable; }
        public String getChangelog() { return changelog; }
        public void setChangelog(String changelog) { this.changelog = changelog; }
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        public String getDownloadUrl() { return downloadUrl; }
        public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    }

    public static class InstallationResult {
        private final boolean success;
        private final Product product;
        private final String errorMessage;

        public InstallationResult(boolean success, Product product, String errorMessage) {
            this.success = success;
            this.product = product;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public Product getProduct() { return product; }
        public String getErrorMessage() { return errorMessage; }
    }
}
