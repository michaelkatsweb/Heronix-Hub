package com.heronixedu.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends encrypted audit events to the SIS Server's secure audit trail.
 *
 * Uses hybrid encryption: AES-256-GCM for the payload, RSA-2048 for the key.
 * The Hub only has the server's public key and cannot decrypt entries.
 * All sends are fire-and-forget to avoid blocking the login flow.
 */
@Service
@Slf4j
public class SecureAuditClient {

    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final NetworkConfigService networkConfigService;
    private final DeviceInfoService deviceInfoService;
    private final ObjectMapper objectMapper;

    private PublicKey serverPublicKey;
    private String cachedServerUrl;

    public SecureAuditClient(NetworkConfigService networkConfigService, DeviceInfoService deviceInfoService) {
        this.networkConfigService = networkConfigService;
        this.deviceInfoService = deviceInfoService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Send an audit event to the SIS server, encrypted.
     * Fire-and-forget: failures are logged but never block the caller.
     */
    public void sendAuditEvent(String serverUrl, String accessToken,
                                String action, String username, String details,
                                boolean success, String severity) {
        // Run async to avoid blocking
        Thread.startVirtualThread(() -> {
            try {
                doSend(serverUrl, accessToken, action, username, details, success, severity);
            } catch (Exception e) {
                log.debug("Failed to send secure audit event: {}", e.getMessage());
            }
        });
    }

    /**
     * Convenience: send with default server URL from the authentication service.
     */
    public void sendAuditEvent(String serverUrl, String accessToken,
                                String action, String username, String details) {
        sendAuditEvent(serverUrl, accessToken, action, username, details, true, "INFO");
    }

    private void doSend(String serverUrl, String accessToken,
                        String action, String username, String details,
                        boolean success, String severity) throws Exception {
        // Ensure we have the server's public key
        fetchPublicKeyIfNeeded(serverUrl, accessToken);
        if (serverPublicKey == null) {
            log.debug("No server public key available, skipping secure audit");
            return;
        }

        // Build the audit event JSON
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("action", action);
        event.put("username", username);
        event.put("details", details);
        event.put("success", success);
        event.put("severity", severity);
        event.put("hostname", deviceInfoService.getHostname());
        event.put("deviceId", deviceInfoService.getDeviceId());
        event.put("macAddress", deviceInfoService.getMacAddress());
        event.put("osInfo", deviceInfoService.getOsInfo());

        String plaintext = objectMapper.writeValueAsString(event);

        // Hash for integrity
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String payloadHash = Base64.getEncoder().encodeToString(
                digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));

        // Generate random AES key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(AES_KEY_SIZE, new SecureRandom());
        SecretKey aesKey = keyGen.generateKey();

        // Encrypt payload with AES-GCM
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher aesCipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);
        byte[] ciphertext = aesCipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // Encrypt AES key with server's RSA public key
        Cipher rsaCipher = Cipher.getInstance(RSA_ALGORITHM);
        rsaCipher.init(Cipher.ENCRYPT_MODE, serverPublicKey);
        byte[] encryptedKey = rsaCipher.doFinal(aesKey.getEncoded());

        // Build request
        Map<String, String> request = new LinkedHashMap<>();
        request.put("hubDeviceId", deviceInfoService.getDeviceId());
        request.put("encryptedPayload", Base64.getEncoder().encodeToString(ciphertext));
        request.put("encryptedKey", Base64.getEncoder().encodeToString(encryptedKey));
        request.put("iv", Base64.getEncoder().encodeToString(iv));
        request.put("payloadHash", payloadHash);

        // POST to SIS server
        String url = serverUrl + "/api/secure-audit/ingest";

        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        if (accessToken != null) {
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        }
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        String jsonBody = objectMapper.writeValueAsString(request);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = connection.getResponseCode();
        connection.disconnect();

        if (responseCode >= 200 && responseCode < 300) {
            log.debug("Secure audit event sent: {} by {}", action, username);
        } else {
            log.debug("Secure audit ingest returned HTTP {}", responseCode);
        }
    }

    private synchronized void fetchPublicKeyIfNeeded(String serverUrl, String accessToken) {
        if (serverPublicKey != null && serverUrl.equals(cachedServerUrl)) {
            return;
        }

        try {
            String url = serverUrl + "/api/secure-audit/public-key";
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            if (accessToken != null) {
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                String response = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode root = objectMapper.readTree(response);
                String pem = root.has("publicKey") ? root.get("publicKey").asText() : null;

                if (pem != null) {
                    serverPublicKey = loadPublicKey(pem);
                    cachedServerUrl = serverUrl;
                    log.info("Secure audit public key fetched from SIS server");
                }
            }
            connection.disconnect();
        } catch (Exception e) {
            log.debug("Could not fetch secure audit public key: {}", e.getMessage());
        }
    }

    private PublicKey loadPublicKey(String pem) throws Exception {
        String stripped = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }
}
