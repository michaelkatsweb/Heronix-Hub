package com.heronixedu.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heronixedu.hub.model.NetworkConfig;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.ServerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * API Client for communicating with Heronix-SIS server
 * Handles authentication and data fetching from the central SIS database
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SisApiClient {

    private final NetworkConfigService networkConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Default SIS server URLs to try
    private static final String[] DEFAULT_SIS_URLS = {
            "http://localhost:9590",
            "http://localhost:9580",
            "http://localhost:8080"
    };

    /**
     * Authenticate user against SIS server
     * @return User object if authentication successful, empty if failed
     */
    public Optional<SisAuthResult> authenticate(String username, String password) {
        List<String> urlsToTry = getSisUrls();

        for (String baseUrl : urlsToTry) {
            try {
                Optional<SisAuthResult> result = tryAuthenticate(baseUrl, username, password);
                if (result.isPresent()) {
                    log.info("Successfully authenticated {} via SIS at {}", username, baseUrl);
                    return result;
                }
            } catch (Exception e) {
                log.debug("Failed to connect to SIS at {}: {}", baseUrl, e.getMessage());
            }
        }

        log.warn("Could not authenticate {} - all SIS servers unavailable", username);
        return Optional.empty();
    }

    private Optional<SisAuthResult> tryAuthenticate(String baseUrl, String username, String password) {
        String authUrl = baseUrl + "/api/auth/login";

        try {
            NetworkConfig config = networkConfigService.getActiveConfig();
            HttpURLConnection connection = createConnection(authUrl, config);

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            // Send login request
            String jsonBody = String.format(
                    "{\"username\":\"%s\",\"password\":\"%s\"}",
                    escapeJson(username), escapeJson(password));

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                // Read response
                String response = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode root = objectMapper.readTree(response);

                if (root.has("success") && root.get("success").asBoolean()) {
                    JsonNode data = root.get("data");

                    SisAuthResult result = new SisAuthResult();
                    result.setAccessToken(data.has("accessToken") ? data.get("accessToken").asText() : null);
                    result.setRefreshToken(data.has("refreshToken") ? data.get("refreshToken").asText() : null);
                    result.setUserId(data.has("userId") ? data.get("userId").asText() : username);

                    // Parse roles
                    if (data.has("roles") && data.get("roles").isArray()) {
                        List<String> roles = new ArrayList<>();
                        for (JsonNode roleNode : data.get("roles")) {
                            roles.add(roleNode.asText());
                        }
                        result.setRoles(roles);
                    }

                    result.setServerUrl(baseUrl);
                    return Optional.of(result);
                }
            } else if (responseCode == 401) {
                // Invalid credentials - don't try other servers
                log.debug("Invalid credentials for {} at {}", username, baseUrl);
                return Optional.empty();
            }

            connection.disconnect();

        } catch (Exception e) {
            log.debug("Error connecting to SIS at {}: {}", authUrl, e.getMessage());
            throw new RuntimeException(e);
        }

        return Optional.empty();
    }

    /**
     * Get user details from SIS server using access token
     */
    public Optional<SisUserInfo> getUserInfo(String accessToken, String serverUrl) {
        String userUrl = serverUrl + "/api/auth/me";

        try {
            NetworkConfig config = networkConfigService.getActiveConfig();
            HttpURLConnection connection = createConnection(userUrl, config);

            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                String response = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode root = objectMapper.readTree(response);

                if (root.has("success") && root.get("success").asBoolean()) {
                    JsonNode data = root.get("data");

                    SisUserInfo userInfo = new SisUserInfo();
                    userInfo.setUserId(data.has("userId") ? data.get("userId").asText() : null);

                    if (data.has("authorities") && data.get("authorities").isArray()) {
                        List<String> authorities = new ArrayList<>();
                        for (JsonNode auth : data.get("authorities")) {
                            if (auth.has("authority")) {
                                authorities.add(auth.get("authority").asText());
                            } else {
                                authorities.add(auth.asText());
                            }
                        }
                        userInfo.setAuthorities(authorities);
                    }

                    return Optional.of(userInfo);
                }
            }

            connection.disconnect();

        } catch (Exception e) {
            log.error("Error fetching user info from SIS: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Test if SIS server is available
     */
    public boolean isSisAvailable() {
        List<String> urlsToTry = getSisUrls();

        for (String baseUrl : urlsToTry) {
            if (testSisConnection(baseUrl)) {
                return true;
            }
        }
        return false;
    }

    public boolean testSisConnection(String baseUrl) {
        try {
            String healthUrl = baseUrl + "/actuator/health";
            NetworkConfig config = networkConfigService.getActiveConfig();
            HttpURLConnection connection = createConnection(healthUrl, config);

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            return responseCode >= 200 && responseCode < 400;

        } catch (Exception e) {
            log.debug("SIS not available at {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    private HttpURLConnection createConnection(String urlString, NetworkConfig config) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection;

        if (config.getProxyEnabled() != null && config.getProxyEnabled()
                && config.getProxyHost() != null && !config.getProxyHost().isEmpty()) {
            Proxy proxy = networkConfigService.getConfiguredProxy(config);
            connection = (HttpURLConnection) url.openConnection(proxy);
        } else {
            connection = (HttpURLConnection) url.openConnection();
        }

        return connection;
    }

    private List<String> getSisUrls() {
        List<String> urls = new ArrayList<>();

        // First try from network config
        NetworkConfig config = networkConfigService.getActiveConfig();

        if (config.getCloudServerUrl() != null && !config.getCloudServerUrl().isEmpty()) {
            urls.add(config.getCloudServerUrl());
        }

        if (config.getLocalServerPath() != null && config.getLocalServerPath().startsWith("http")) {
            urls.add(config.getLocalServerPath());
        }

        // Add defaults
        for (String defaultUrl : DEFAULT_SIS_URLS) {
            if (!urls.contains(defaultUrl)) {
                urls.add(defaultUrl);
            }
        }

        return urls;
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ========================================================================
    // DEVICE MANAGEMENT API
    // ========================================================================

    /**
     * Register a device with the SIS server for approval
     */
    public boolean registerDevice(String serverUrl, String accessToken,
                                   String deviceId, String hostname, String macAddress,
                                   String osInfo, String accountId) {
        String url = serverUrl + "/api/secure-sync/devices/register";

        try {
            NetworkConfig config = networkConfigService.getActiveConfig();
            HttpURLConnection connection = createConnection(url, config);

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            String jsonBody = objectMapper.writeValueAsString(
                    java.util.Map.of(
                            "deviceId", deviceId,
                            "deviceName", hostname,
                            "macAddress", macAddress,
                            "operatingSystem", osInfo,
                            "accountToken", accountId
                    )
            );

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            if (responseCode >= 200 && responseCode < 300) {
                log.info("Device registered with SIS: {} ({})", hostname, deviceId.substring(0, 8));
                return true;
            } else {
                log.warn("Device registration failed: HTTP {}", responseCode);
                return false;
            }

        } catch (Exception e) {
            log.error("Error registering device with SIS: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check device approval status with the SIS server
     * @return Device status string (ACTIVE, PENDING_APPROVAL, etc.) or empty if not found/error
     */
    public Optional<String> checkDeviceStatus(String serverUrl, String accessToken, String deviceId) {
        String url = serverUrl + "/api/secure-sync/devices/" + deviceId + "/status";

        try {
            NetworkConfig config = networkConfigService.getActiveConfig();
            HttpURLConnection connection = createConnection(url, config);

            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                String response = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode root = objectMapper.readTree(response);

                String status = root.has("status") ? root.get("status").asText() : null;
                connection.disconnect();
                return Optional.ofNullable(status);
            } else if (responseCode == 404) {
                connection.disconnect();
                return Optional.empty(); // Device not registered
            }

            connection.disconnect();

        } catch (Exception e) {
            log.debug("Error checking device status: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Get list of pending device registrations (for admin panel)
     */
    public List<DeviceSummary> getPendingDevices(String serverUrl, String accessToken) {
        String url = serverUrl + "/api/secure-sync/devices/pending";
        List<DeviceSummary> devices = new ArrayList<>();

        try {
            NetworkConfig config = networkConfigService.getActiveConfig();
            HttpURLConnection connection = createConnection(url, config);

            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                String response = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode root = objectMapper.readTree(response);

                // Response may be a direct array or wrapped: {"pendingDevices": [...]}
                JsonNode deviceArray = root.isArray() ? root
                        : (root.has("pendingDevices") ? root.get("pendingDevices") : null);

                if (deviceArray != null && deviceArray.isArray()) {
                    for (JsonNode node : deviceArray) {
                        DeviceSummary device = new DeviceSummary();
                        device.setDeviceId(node.has("deviceId") ? node.get("deviceId").asText() : "");
                        device.setDeviceName(node.has("deviceName") ? node.get("deviceName").asText() : "");
                        device.setMacAddress(node.has("macAddress") ? node.get("macAddress").asText() : "");
                        device.setOperatingSystem(node.has("operatingSystem") ? node.get("operatingSystem").asText() : "");
                        device.setAccountToken(node.has("accountToken") ? node.get("accountToken").asText() : "");
                        device.setStatus(node.has("status") ? node.get("status").asText() : "PENDING_APPROVAL");
                        device.setRegisteredAt(node.has("registrationRequestedAt") ? node.get("registrationRequestedAt").asText() : "");
                        devices.add(device);
                    }
                }
            }

            connection.disconnect();

        } catch (Exception e) {
            log.error("Error fetching pending devices: {}", e.getMessage());
        }

        return devices;
    }

    /**
     * Approve a device registration
     */
    public boolean approveDevice(String serverUrl, String accessToken, String deviceId, String approvedBy) {
        String url = serverUrl + "/api/secure-sync/devices/" + deviceId + "/approve";

        try {
            NetworkConfig config = networkConfigService.getActiveConfig();
            HttpURLConnection connection = createConnection(url, config);

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("X-Approved-By", approvedBy);
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            String jsonBody = objectMapper.writeValueAsString(
                    java.util.Map.of("approvedBy", approvedBy)
            );

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            if (responseCode >= 200 && responseCode < 300) {
                log.info("Device approved: {}", deviceId.substring(0, 8));
                return true;
            }

        } catch (Exception e) {
            log.error("Error approving device: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Reject a device registration
     */
    public boolean rejectDevice(String serverUrl, String accessToken, String deviceId, String rejectedBy, String reason) {
        String url = serverUrl + "/api/secure-sync/devices/" + deviceId + "/reject";

        try {
            NetworkConfig config = networkConfigService.getActiveConfig();
            HttpURLConnection connection = createConnection(url, config);

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            String jsonBody = objectMapper.writeValueAsString(
                    java.util.Map.of("rejectedBy", rejectedBy, "reason", reason != null ? reason : "")
            );

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            if (responseCode >= 200 && responseCode < 300) {
                log.info("Device rejected: {}", deviceId.substring(0, 8));
                return true;
            }

        } catch (Exception e) {
            log.error("Error rejecting device: {}", e.getMessage());
        }
        return false;
    }

    // Result classes

    public static class DeviceSummary {
        private String deviceId;
        private String deviceName;
        private String macAddress;
        private String operatingSystem;
        private String accountToken;
        private String status;
        private String registeredAt;

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
        public String getMacAddress() { return macAddress; }
        public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
        public String getOperatingSystem() { return operatingSystem; }
        public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
        public String getAccountToken() { return accountToken; }
        public void setAccountToken(String accountToken) { this.accountToken = accountToken; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getRegisteredAt() { return registeredAt; }
        public void setRegisteredAt(String registeredAt) { this.registeredAt = registeredAt; }
    }

    public static class SisAuthResult {
        private String accessToken;
        private String refreshToken;
        private String userId;
        private List<String> roles;
        private String serverUrl;

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
        public String getServerUrl() { return serverUrl; }
        public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
    }

    public static class SisUserInfo {
        private String userId;
        private List<String> authorities;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public List<String> getAuthorities() { return authorities; }
        public void setAuthorities(List<String> authorities) { this.authorities = authorities; }
    }
}
