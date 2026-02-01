package com.heronixedu.hub.model.enums;

public enum ServerType {
    LOCAL("Local Network (SMB/Windows Share)"),
    CLOUD("Cloud Server (HTTP/HTTPS)"),
    AUTO("Auto-detect");

    private final String displayName;

    ServerType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
