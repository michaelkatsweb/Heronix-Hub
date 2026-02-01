package com.heronixedu.hub.service;

import com.heronixedu.hub.model.NetworkConfig;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.ServerType;
import com.heronixedu.hub.repository.NetworkConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.time.LocalDateTime;

@Service
@Slf4j
public class NetworkConfigService {

    private final NetworkConfigRepository networkConfigRepository;
    private final AuditLogService auditLogService;

    public NetworkConfigService(NetworkConfigRepository networkConfigRepository,
                                @Lazy AuditLogService auditLogService) {
        this.networkConfigRepository = networkConfigRepository;
        this.auditLogService = auditLogService;
    }

    public NetworkConfig getActiveConfig() {
        return networkConfigRepository.findByIsActiveTrue()
                .orElseGet(this::createDefaultConfig);
    }

    @Transactional
    public NetworkConfig createDefaultConfig() {
        NetworkConfig config = NetworkConfig.builder()
                .configName("default")
                .serverType(ServerType.AUTO)
                .isActive(true)
                .build();
        return networkConfigRepository.save(config);
    }

    @Transactional
    public NetworkConfig updateConfig(NetworkConfig config, User updatedBy) {
        config.setUpdatedAt(LocalDateTime.now());
        NetworkConfig saved = networkConfigRepository.save(config);

        auditLogService.logNetworkConfigChange(updatedBy,
                String.format("Updated network config: serverType=%s, localPath=%s, cloudUrl=%s",
                        config.getServerType(), config.getLocalServerPath(), config.getCloudServerUrl()));

        log.info("Network config updated by {}", updatedBy.getUsername());
        return saved;
    }

    public ServerConnectionResult testConnection(NetworkConfig config) {
        ServerConnectionResult result = new ServerConnectionResult();

        if (config.getServerType() == ServerType.AUTO || config.getServerType() == ServerType.LOCAL) {
            result.setLocalAvailable(testLocalServer(config.getLocalServerPath()));
        }

        if (config.getServerType() == ServerType.AUTO || config.getServerType() == ServerType.CLOUD) {
            result.setCloudAvailable(testCloudServer(config.getCloudServerUrl(), config));
        }

        // Determine recommended type
        if (config.getServerType() == ServerType.AUTO) {
            result.setRecommendedType(result.isLocalAvailable() ? ServerType.LOCAL : ServerType.CLOUD);
        } else {
            result.setRecommendedType(config.getServerType());
        }

        return result;
    }

    public ServerConnectionResult testConnectionAndLog(NetworkConfig config, User user) {
        ServerConnectionResult result = testConnection(config);

        // Update config with test results
        config.setLastConnectionTest(LocalDateTime.now());
        config.setConnectionStatus(result.isAnyAvailable() ? "Connected" : "Failed");
        networkConfigRepository.save(config);

        // Log the test
        auditLogService.logServerConnectionTest(user, result.isAnyAvailable(),
                String.format("Local: %s, Cloud: %s",
                        result.isLocalAvailable() ? "OK" : "FAILED",
                        result.isCloudAvailable() ? "OK" : "FAILED"));

        return result;
    }

    private boolean testLocalServer(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        try {
            // For Windows UNC paths (\\SERVER\share)
            if (path.startsWith("\\\\") || path.startsWith("//")) {
                File file = new File(path);
                return file.exists() && file.isDirectory();
            }

            // For regular file paths
            File file = new File(path);
            return file.exists() && file.isDirectory();

        } catch (Exception e) {
            log.error("Error testing local server: {}", path, e);
            return false;
        }
    }

    private boolean testCloudServer(String url, NetworkConfig config) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        HttpURLConnection connection = null;
        try {
            URL serverUrl = new URL(url);

            // Apply proxy if configured
            if (config.getProxyEnabled() && config.getProxyHost() != null) {
                Proxy proxy = getConfiguredProxy(config);
                connection = (HttpURLConnection) serverUrl.openConnection(proxy);
            } else {
                connection = (HttpURLConnection) serverUrl.openConnection();
            }

            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            return responseCode >= 200 && responseCode < 400;

        } catch (Exception e) {
            log.error("Error testing cloud server: {}", url, e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public Proxy getConfiguredProxy(NetworkConfig config) {
        if (!config.getProxyEnabled() || config.getProxyHost() == null) {
            return Proxy.NO_PROXY;
        }

        Proxy.Type type;
        if ("SOCKS5".equals(config.getProxyType()) || "SOCKS4".equals(config.getProxyType())) {
            type = Proxy.Type.SOCKS;
        } else {
            type = Proxy.Type.HTTP;
        }

        return new Proxy(type, new InetSocketAddress(config.getProxyHost(), config.getProxyPort()));
    }

    public String resolveServerPath(NetworkConfig config) {
        ServerType effectiveType = config.getServerType();

        if (effectiveType == ServerType.AUTO) {
            // Try local first
            if (testLocalServer(config.getLocalServerPath())) {
                return config.getLocalServerPath();
            }
            // Fall back to cloud
            return config.getCloudServerUrl();
        } else if (effectiveType == ServerType.LOCAL) {
            return config.getLocalServerPath();
        } else {
            return config.getCloudServerUrl();
        }
    }

    // Inner class for connection test results
    public static class ServerConnectionResult {
        private boolean localAvailable;
        private boolean cloudAvailable;
        private ServerType recommendedType;

        public boolean isLocalAvailable() {
            return localAvailable;
        }

        public void setLocalAvailable(boolean localAvailable) {
            this.localAvailable = localAvailable;
        }

        public boolean isCloudAvailable() {
            return cloudAvailable;
        }

        public void setCloudAvailable(boolean cloudAvailable) {
            this.cloudAvailable = cloudAvailable;
        }

        public ServerType getRecommendedType() {
            return recommendedType;
        }

        public void setRecommendedType(ServerType recommendedType) {
            this.recommendedType = recommendedType;
        }

        public boolean isAnyAvailable() {
            return localAvailable || cloudAvailable;
        }
    }
}
