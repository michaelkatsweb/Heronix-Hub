package com.heronixedu.hub.exception;

/**
 * Thrown when a user attempts to log in from a device that has not been
 * approved by an IT administrator.
 */
public class DeviceNotApprovedException extends RuntimeException {

    private final String deviceId;
    private final String hostname;

    public DeviceNotApprovedException(String message, String deviceId, String hostname) {
        super(message);
        this.deviceId = deviceId;
        this.hostname = hostname;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getHostname() {
        return hostname;
    }
}
