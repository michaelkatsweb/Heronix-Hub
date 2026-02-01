package com.heronixedu.hub.service;

import com.heronixedu.hub.exception.DeviceNotApprovedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrates device approval checking and registration with the SIS Server.
 * Called during login to ensure the device is approved before granting access.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceApprovalService {

    private final DeviceInfoService deviceInfoService;
    private final SisApiClient sisApiClient;

    /**
     * Check if the current device is approved for the given user.
     * SUPERADMIN and IT_ADMIN bypass device checks.
     * If the device is unknown, registers it as PENDING_APPROVAL.
     *
     * @throws DeviceNotApprovedException if device is not approved
     */
    public void checkDeviceApproval(String serverUrl, String accessToken, String accountId, String userRole) {
        // Admins bypass device checks
        if ("SUPERADMIN".equalsIgnoreCase(userRole) || "IT_ADMIN".equalsIgnoreCase(userRole)) {
            log.debug("Device check bypassed for role: {}", userRole);
            return;
        }

        String deviceId = deviceInfoService.getDeviceId();
        String hostname = deviceInfoService.getHostname();

        log.info("Checking device approval for {} on device {} ({})", accountId, hostname, deviceId.substring(0, 8));

        // Check status with SIS server
        Optional<String> status = sisApiClient.checkDeviceStatus(serverUrl, accessToken, deviceId);

        if (status.isPresent()) {
            String deviceStatus = status.get();

            if ("ACTIVE".equalsIgnoreCase(deviceStatus)) {
                log.info("Device approved: {} ({})", hostname, deviceId.substring(0, 8));
                return;
            }

            if ("PENDING_APPROVAL".equalsIgnoreCase(deviceStatus)) {
                log.info("Device pending approval: {} ({})", hostname, deviceId.substring(0, 8));
                throw new DeviceNotApprovedException(
                        "This device is pending approval by your IT administrator.",
                        deviceId, hostname);
            }

            // REVOKED, REJECTED, SUSPENDED
            log.warn("Device access denied (status: {}): {} ({})", deviceStatus, hostname, deviceId.substring(0, 8));
            throw new DeviceNotApprovedException(
                    "This device has been " + deviceStatus.toLowerCase().replace('_', ' ') + ". Contact your IT administrator.",
                    deviceId, hostname);
        }

        // Device not found - register it
        log.info("Device not registered, registering: {} ({})", hostname, deviceId.substring(0, 8));
        sisApiClient.registerDevice(serverUrl, accessToken, deviceId,
                hostname, deviceInfoService.getMacAddress(),
                deviceInfoService.getOsInfo(), accountId);

        throw new DeviceNotApprovedException(
                "This device has been registered and is pending approval by your IT administrator.",
                deviceId, hostname);
    }

    /**
     * Get pending devices for admin review
     */
    public List<SisApiClient.DeviceSummary> getPendingDevices(String serverUrl, String accessToken) {
        return sisApiClient.getPendingDevices(serverUrl, accessToken);
    }

    /**
     * Approve a device
     */
    public boolean approveDevice(String serverUrl, String accessToken, String deviceId, String approvedBy) {
        return sisApiClient.approveDevice(serverUrl, accessToken, deviceId, approvedBy);
    }

    /**
     * Reject a device
     */
    public boolean rejectDevice(String serverUrl, String accessToken, String deviceId, String rejectedBy, String reason) {
        return sisApiClient.rejectDevice(serverUrl, accessToken, deviceId, rejectedBy, reason);
    }
}
