package com.heronixedu.hub.model.enums;

/**
 * Categories for third-party applications in the software catalog.
 */
public enum ThirdPartyAppCategory {
    PRODUCTIVITY("Productivity", "Office suites, document editors, note-taking apps"),
    BROWSER("Web Browsers", "Internet browsers and web tools"),
    COMMUNICATION("Communication", "Email clients, messaging, video conferencing"),
    DEVELOPMENT("Development Tools", "IDEs, code editors, development utilities"),
    SECURITY("Security", "Antivirus, firewalls, security tools"),
    MULTIMEDIA("Multimedia", "Media players, image editors, audio tools"),
    UTILITIES("Utilities", "System utilities, file managers, archivers"),
    EDUCATION("Education", "Learning tools, educational software"),
    ACCESSIBILITY("Accessibility", "Screen readers, magnifiers, accessibility tools"),
    GRAPHICS("Graphics & Design", "Image editing, CAD, design software"),
    SCIENCE("Science & Math", "Scientific calculators, simulation tools"),
    REFERENCE("Reference", "Dictionaries, encyclopedias, documentation viewers"),
    OTHER("Other", "Miscellaneous applications");

    private final String displayName;
    private final String description;

    ThirdPartyAppCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
