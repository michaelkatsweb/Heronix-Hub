package com.heronixedu.hub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Enumeration;

/**
 * Collects machine identification info for device approval.
 * Generates a stable device fingerprint from hostname + MAC + OS.
 */
@Service
@Slf4j
public class DeviceInfoService {

    private String cachedDeviceId;
    private String cachedHostname;
    private String cachedMacAddress;
    private String cachedOsInfo;

    public String getDeviceId() {
        if (cachedDeviceId == null) {
            String raw = getHostname() + "|" + getMacAddress() + "|" + getOsInfo() + "|" + System.getProperty("user.name");
            cachedDeviceId = sha256(raw);
        }
        return cachedDeviceId;
    }

    public String getHostname() {
        if (cachedHostname == null) {
            try {
                cachedHostname = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                log.warn("Could not determine hostname: {}", e.getMessage());
                cachedHostname = "UNKNOWN";
            }
        }
        return cachedHostname;
    }

    public String getMacAddress() {
        if (cachedMacAddress == null) {
            try {
                cachedMacAddress = findPrimaryMacAddress();
            } catch (Exception e) {
                log.warn("Could not determine MAC address: {}", e.getMessage());
                cachedMacAddress = "00:00:00:00:00:00";
            }
        }
        return cachedMacAddress;
    }

    public String getOsInfo() {
        if (cachedOsInfo == null) {
            cachedOsInfo = System.getProperty("os.name") + " " + System.getProperty("os.version");
        }
        return cachedOsInfo;
    }

    private String findPrimaryMacAddress() throws Exception {
        // Try local host first
        InetAddress localHost = InetAddress.getLocalHost();
        NetworkInterface ni = NetworkInterface.getByInetAddress(localHost);
        if (ni != null && ni.getHardwareAddress() != null) {
            return formatMac(ni.getHardwareAddress());
        }

        // Fallback: find first non-loopback interface with a MAC
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (!iface.isLoopback() && iface.isUp() && iface.getHardwareAddress() != null) {
                return formatMac(iface.getHardwareAddress());
            }
        }
        return "00:00:00:00:00:00";
    }

    private String formatMac(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", mac[i]));
        }
        return sb.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.error("SHA-256 hashing failed", e);
            return input.hashCode() + "";
        }
    }
}
