package com.heronixedu.hub.service;

import com.heronixedu.hub.model.NetworkConfig;
import com.heronixedu.hub.repository.ProductRepository;
import com.heronixedu.hub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemStatusService {

    private final NetworkConfigService networkConfigService;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final DataSource dataSource;

    public SystemStatus getSystemStatus() {
        SystemStatus status = new SystemStatus();

        // Database status
        status.setDatabaseOnline(checkDatabaseConnection());

        // Server connectivity
        NetworkConfig config = networkConfigService.getActiveConfig();
        NetworkConfigService.ServerConnectionResult serverStatus = networkConfigService.testConnection(config);
        status.setLocalServerAvailable(serverStatus.isLocalAvailable());
        status.setCloudServerAvailable(serverStatus.isCloudAvailable());
        status.setServerType(config.getServerType().getDisplayName());

        // Statistics
        status.setTotalUsers(userRepository.count());
        status.setTotalProducts(productRepository.count());
        status.setInstalledProducts(productRepository.findAllByIsInstalledTrue().size());

        // Java/System info
        status.setJavaVersion(System.getProperty("java.version"));
        status.setJavaVendor(System.getProperty("java.vendor"));
        status.setOsName(System.getProperty("os.name"));
        status.setOsVersion(System.getProperty("os.version"));
        status.setOsArch(System.getProperty("os.arch"));

        // Memory info
        Runtime runtime = Runtime.getRuntime();
        status.setMaxMemory(runtime.maxMemory());
        status.setTotalMemory(runtime.totalMemory());
        status.setFreeMemory(runtime.freeMemory());
        status.setUsedMemory(runtime.totalMemory() - runtime.freeMemory());

        // Hub version
        status.setHubVersion("1.0.0");

        return status;
    }

    private boolean checkDatabaseConnection() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5);
        } catch (Exception e) {
            log.error("Database connection check failed", e);
            return false;
        }
    }

    public static class SystemStatus {
        private boolean databaseOnline;
        private boolean localServerAvailable;
        private boolean cloudServerAvailable;
        private String serverType;

        private long totalUsers;
        private long totalProducts;
        private long installedProducts;

        private String javaVersion;
        private String javaVendor;
        private String osName;
        private String osVersion;
        private String osArch;

        private long maxMemory;
        private long totalMemory;
        private long freeMemory;
        private long usedMemory;

        private String hubVersion;

        // Getters and Setters
        public boolean isDatabaseOnline() { return databaseOnline; }
        public void setDatabaseOnline(boolean databaseOnline) { this.databaseOnline = databaseOnline; }

        public boolean isLocalServerAvailable() { return localServerAvailable; }
        public void setLocalServerAvailable(boolean localServerAvailable) { this.localServerAvailable = localServerAvailable; }

        public boolean isCloudServerAvailable() { return cloudServerAvailable; }
        public void setCloudServerAvailable(boolean cloudServerAvailable) { this.cloudServerAvailable = cloudServerAvailable; }

        public String getServerType() { return serverType; }
        public void setServerType(String serverType) { this.serverType = serverType; }

        public long getTotalUsers() { return totalUsers; }
        public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }

        public long getTotalProducts() { return totalProducts; }
        public void setTotalProducts(long totalProducts) { this.totalProducts = totalProducts; }

        public long getInstalledProducts() { return installedProducts; }
        public void setInstalledProducts(long installedProducts) { this.installedProducts = installedProducts; }

        public String getJavaVersion() { return javaVersion; }
        public void setJavaVersion(String javaVersion) { this.javaVersion = javaVersion; }

        public String getJavaVendor() { return javaVendor; }
        public void setJavaVendor(String javaVendor) { this.javaVendor = javaVendor; }

        public String getOsName() { return osName; }
        public void setOsName(String osName) { this.osName = osName; }

        public String getOsVersion() { return osVersion; }
        public void setOsVersion(String osVersion) { this.osVersion = osVersion; }

        public String getOsArch() { return osArch; }
        public void setOsArch(String osArch) { this.osArch = osArch; }

        public long getMaxMemory() { return maxMemory; }
        public void setMaxMemory(long maxMemory) { this.maxMemory = maxMemory; }

        public long getTotalMemory() { return totalMemory; }
        public void setTotalMemory(long totalMemory) { this.totalMemory = totalMemory; }

        public long getFreeMemory() { return freeMemory; }
        public void setFreeMemory(long freeMemory) { this.freeMemory = freeMemory; }

        public long getUsedMemory() { return usedMemory; }
        public void setUsedMemory(long usedMemory) { this.usedMemory = usedMemory; }

        public String getHubVersion() { return hubVersion; }
        public void setHubVersion(String hubVersion) { this.hubVersion = hubVersion; }

        public double getMemoryUsagePercent() {
            return maxMemory > 0 ? (double) usedMemory / maxMemory * 100 : 0;
        }

        public String getFormattedUsedMemory() {
            return formatBytes(usedMemory);
        }

        public String getFormattedMaxMemory() {
            return formatBytes(maxMemory);
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
