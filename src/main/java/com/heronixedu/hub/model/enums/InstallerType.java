package com.heronixedu.hub.model.enums;

/**
 * Types of installers supported for third-party application deployment.
 */
public enum InstallerType {
    EXE("Executable Installer", ".exe", "/S /silent /quiet"),
    MSI("Windows Installer", ".msi", "/qn /norestart"),
    MSIX("MSIX Package", ".msix", ""),
    ZIP("ZIP Archive", ".zip", ""),
    PORTABLE("Portable Application", "", ""),
    WINGET("Windows Package Manager", "", ""),
    CHOCOLATEY("Chocolatey Package", "", "");

    private final String displayName;
    private final String fileExtension;
    private final String defaultSilentArgs;

    InstallerType(String displayName, String fileExtension, String defaultSilentArgs) {
        this.displayName = displayName;
        this.fileExtension = fileExtension;
        this.defaultSilentArgs = defaultSilentArgs;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public String getDefaultSilentArgs() {
        return defaultSilentArgs;
    }
}
