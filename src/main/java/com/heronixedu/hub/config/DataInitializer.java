package com.heronixedu.hub.config;

import com.heronixedu.hub.model.*;
import com.heronixedu.hub.model.enums.InstallerType;
import com.heronixedu.hub.model.enums.PermissionType;
import com.heronixedu.hub.model.enums.RoleType;
import com.heronixedu.hub.model.enums.ServerType;
import com.heronixedu.hub.model.enums.ThirdPartyAppCategory;
import com.heronixedu.hub.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.heronixedu.hub.model.AppAccessPolicy;
import com.heronixedu.hub.model.AppAccessPolicy.PolicyTargetType;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final NetworkConfigRepository networkConfigRepository;
    private final ThirdPartyAppRepository thirdPartyAppRepository;
    private final AppAccessPolicyRepository appAccessPolicyRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    @Override
    @Transactional
    public void run(String... args) {
        initializePermissions();
        initializeRoles();
        initializeDefaultUser();
        initializeProducts();
        initializeNetworkConfig();
        initializeThirdPartyApps();
        initializeAccessPolicies();
    }

    private void initializePermissions() {
        for (PermissionType permType : PermissionType.values()) {
            if (!permissionRepository.existsByPermissionName(permType.name())) {
                Permission permission = new Permission(
                        permType.name(),
                        permType.getDisplayName(),
                        permType.getDescription(),
                        permType.getCategory()
                );
                permissionRepository.save(permission);
                log.info("Created permission: {}", permType.name());
            }
        }
        log.info("Initialized {} permissions", permissionRepository.count());
    }

    private void initializeRoles() {
        for (RoleType roleType : RoleType.values()) {
            if (!roleRepository.existsByRoleName(roleType.name())) {
                Role role = new Role(
                        roleType.name(),
                        roleType.getDisplayName(),
                        roleType.name() + " role",
                        true
                );

                // Assign permissions to role
                Set<Permission> permissions = new HashSet<>();
                for (PermissionType permType : roleType.getPermissions()) {
                    permissionRepository.findByPermissionName(permType.name())
                            .ifPresent(permissions::add);
                }
                role.setPermissions(permissions);

                roleRepository.save(role);
                log.info("Created role: {} with {} permissions", roleType.name(), permissions.size());
            }
        }
        log.info("Initialized {} roles", roleRepository.count());
    }

    private void initializeDefaultUser() {
        // Create default users for each role
        createUserIfNotExists("admin", "admin", "Super Admin", "SUPERADMIN");
        createUserIfNotExists("itadmin", "itadmin", "IT Administrator", "IT_ADMIN");
        createUserIfNotExists("teacher", "teacher", "Demo Teacher", "TEACHER");
        createUserIfNotExists("student", "student", "Demo Student", "STUDENT");
    }

    private void createUserIfNotExists(String username, String password, String fullName, String roleName) {
        if (!userRepository.existsByUsername(username)) {
            String hashedPassword = passwordEncoder.encode(password);
            User user = new User(username, hashedPassword, fullName, roleName);
            user.setIsActive(true);

            // Assign role entity
            roleRepository.findByRoleName(roleName).ifPresent(user::setRoleEntity);

            userRepository.save(user);
            log.info("Created default {} user ({}/{})", roleName, username, password);
        } else {
            // Update existing user to have role entity if missing
            userRepository.findByUsername(username).ifPresent(user -> {
                if (user.getRoleEntity() == null) {
                    roleRepository.findByRoleName(roleName).ifPresent(role -> {
                        user.setRoleEntity(role);
                        userRepository.save(user);
                        log.info("Updated {} user with {} role entity", username, roleName);
                    });
                }
            });
        }
    }

    private void initializeProducts() {
        // Products are now auto-discovered by ProductLauncherService
        // This method is kept for backwards compatibility but does nothing
        // Auto-discovery will find all Heronix-* directories with pom.xml
        log.info("Product initialization delegated to auto-discovery. Current count: {}", productRepository.count());
    }

    private void initializeNetworkConfig() {
        if (!networkConfigRepository.findByConfigName("default").isPresent()) {
            NetworkConfig config = NetworkConfig.builder()
                    .configName("default")
                    .serverType(ServerType.AUTO)
                    .localServerPath("\\\\SERVER\\heronix")
                    .cloudServerUrl("https://downloads.heronix.com")
                    .isActive(true)
                    .proxyEnabled(false)
                    .customDnsEnabled(false)
                    .sslEnabled(false)
                    .sslVerifyHostname(true)
                    .requiredOutboundPorts("80,443,8080,8081")
                    .build();
            networkConfigRepository.save(config);
            log.info("Created default network configuration");
        }
    }

    private void initializeThirdPartyApps() {
        // Pre-approved educational and productivity software commonly used in schools

        // Web Browsers
        createThirdPartyAppIfNotExists(
                "CHROME", "Google Chrome", "Google LLC",
                ThirdPartyAppCategory.BROWSER, InstallerType.MSI,
                "https://dl.google.com/chrome/install/latest/chrome_installer.exe",
                "/silent /install", "1.0.0",
                "Fast, secure web browser from Google",
                true, false
        );

        createThirdPartyAppIfNotExists(
                "FIREFOX", "Mozilla Firefox", "Mozilla Foundation",
                ThirdPartyAppCategory.BROWSER, InstallerType.EXE,
                "https://download.mozilla.org/?product=firefox-latest&os=win64&lang=en-US",
                "-ms", "1.0.0",
                "Privacy-focused open source web browser",
                true, false
        );

        // Productivity
        createThirdPartyAppIfNotExists(
                "LIBREOFFICE", "LibreOffice", "The Document Foundation",
                ThirdPartyAppCategory.PRODUCTIVITY, InstallerType.MSI,
                null, "/qn", "7.6.0",
                "Free and open source office suite - includes Writer, Calc, Impress",
                true, false
        );

        createThirdPartyAppIfNotExists(
                "NOTEPADPP", "Notepad++", "Don Ho",
                ThirdPartyAppCategory.UTILITIES, InstallerType.EXE,
                "https://github.com/notepad-plus-plus/notepad-plus-plus/releases/latest",
                "/S", "8.6.0",
                "Free source code editor supporting multiple languages",
                false, false
        );

        // Communication
        createThirdPartyAppIfNotExists(
                "ZOOM", "Zoom", "Zoom Video Communications",
                ThirdPartyAppCategory.COMMUNICATION, InstallerType.MSI,
                null, "/qn /norestart", "5.17.0",
                "Video conferencing and online meetings",
                true, false
        );

        createThirdPartyAppIfNotExists(
                "TEAMS", "Microsoft Teams", "Microsoft Corporation",
                ThirdPartyAppCategory.COMMUNICATION, InstallerType.EXE,
                null, "-s", "1.6.0",
                "Team collaboration and communication platform",
                true, false
        );

        // Education
        createThirdPartyAppIfNotExists(
                "GEOGEBRA", "GeoGebra", "GeoGebra GmbH",
                ThirdPartyAppCategory.EDUCATION, InstallerType.EXE,
                "https://download.geogebra.org/installers/6.0/GeoGebra-Windows-Installer-6-0.exe",
                "/S", "6.0.0",
                "Dynamic mathematics software for geometry, algebra, and calculus",
                true, false
        );

        createThirdPartyAppIfNotExists(
                "SCRATCH", "Scratch Desktop", "MIT Media Lab",
                ThirdPartyAppCategory.EDUCATION, InstallerType.EXE,
                null, "/S", "3.0.0",
                "Visual programming language for learning to code",
                true, false
        );

        // Multimedia
        createThirdPartyAppIfNotExists(
                "VLC", "VLC Media Player", "VideoLAN",
                ThirdPartyAppCategory.MULTIMEDIA, InstallerType.EXE,
                "https://get.videolan.org/vlc/last/win64/vlc-3.0.20-win64.exe",
                "/S", "3.0.20",
                "Free and open source cross-platform multimedia player",
                false, false
        );

        createThirdPartyAppIfNotExists(
                "AUDACITY", "Audacity", "Audacity Team",
                ThirdPartyAppCategory.MULTIMEDIA, InstallerType.EXE,
                null, "/VERYSILENT", "3.4.0",
                "Free, open source audio software for recording and editing",
                false, false
        );

        // Utilities
        createThirdPartyAppIfNotExists(
                "SEVENZIP", "7-Zip", "Igor Pavlov",
                ThirdPartyAppCategory.UTILITIES, InstallerType.EXE,
                "https://www.7-zip.org/a/7z2301-x64.exe",
                "/S", "23.01",
                "Free file archiver with high compression ratio",
                false, false
        );

        createThirdPartyAppIfNotExists(
                "ADOBEREADER", "Adobe Acrobat Reader", "Adobe Inc.",
                ThirdPartyAppCategory.UTILITIES, InstallerType.EXE,
                null, "/sAll /rs", "2024.0",
                "View, print, and annotate PDF documents",
                true, false
        );

        // Development (for computer science classes)
        createThirdPartyAppIfNotExists(
                "VSCODE", "Visual Studio Code", "Microsoft Corporation",
                ThirdPartyAppCategory.DEVELOPMENT, InstallerType.EXE,
                "https://code.visualstudio.com/sha/download?build=stable&os=win32-x64",
                "/VERYSILENT /MERGETASKS=!runcode", "1.85.0",
                "Free source code editor with debugging support",
                false, false
        );

        createThirdPartyAppIfNotExists(
                "PYTHON", "Python", "Python Software Foundation",
                ThirdPartyAppCategory.DEVELOPMENT, InstallerType.EXE,
                "https://www.python.org/ftp/python/3.12.0/python-3.12.0-amd64.exe",
                "/quiet InstallAllUsers=1 PrependPath=1", "3.12.0",
                "Popular programming language for education and development",
                false, false
        );

        // Science
        createThirdPartyAppIfNotExists(
                "STELLARIUM", "Stellarium", "Stellarium Developers",
                ThirdPartyAppCategory.SCIENCE, InstallerType.EXE,
                null, "/S", "24.1",
                "Free open source planetarium for your computer",
                true, false
        );

        // Accessibility
        createThirdPartyAppIfNotExists(
                "NVDA", "NVDA Screen Reader", "NV Access",
                ThirdPartyAppCategory.ACCESSIBILITY, InstallerType.EXE,
                "https://www.nvaccess.org/download/",
                "--install-silent", "2024.1",
                "Free screen reader for visually impaired users",
                true, false
        );

        log.info("Initialized {} third-party applications", thirdPartyAppRepository.count());
    }

    private void createThirdPartyAppIfNotExists(String code, String name, String publisher,
                                                  ThirdPartyAppCategory category, InstallerType installerType,
                                                  String downloadUrl, String silentArgs, String version,
                                                  String description, boolean preApproved, boolean requiresRestart) {
        if (!thirdPartyAppRepository.existsByAppCode(code)) {
            ThirdPartyApp app = ThirdPartyApp.builder()
                    .appCode(code)
                    .appName(name)
                    .publisher(publisher)
                    .category(category)
                    .installerType(installerType)
                    .downloadUrl(downloadUrl)
                    .silentInstallArgs(silentArgs)
                    .latestVersion(version)
                    .description(description)
                    .isApproved(preApproved)
                    .isInstalled(false)
                    .requiresAdmin(true)
                    .requiresRestart(requiresRestart)
                    .supportedArchitectures("x64,x86")
                    .licenseType("Free/Open Source")
                    .build();

            if (preApproved) {
                app.setApprovedBy("system");
                app.setApprovedAt(java.time.LocalDateTime.now());
            }

            thirdPartyAppRepository.save(app);
            log.info("Created third-party app: {} (approved: {})", name, preApproved);
        }
    }

    /**
     * Initialize default access policies.
     * Strict by default: only explicitly granted access is allowed.
     *
     * Default policies:
     * - IT_ADMIN: All Heronix products + all third-party categories
     * - TEACHER: Teacher Portal + EdGames + BROWSER & EDUCATION categories
     * - STUDENT: Student Portal + EdGames + EDUCATION category
     */
    private void initializeAccessPolicies() {
        if (appAccessPolicyRepository.count() > 0) {
            log.info("Access policies already initialized (count: {})", appAccessPolicyRepository.count());
            return;
        }

        log.info("Initializing default access policies...");

        // --- IT_ADMIN: full access to all products and categories ---
        String[] allProducts = {"SIS", "TEACHER", "STUDENT", "SCHEDULER", "TALK", "TALKMODULE",
                "GUARDIAN", "GUARDIANMONITOR", "EDGAMES", "HUB", "MESSAGING"};
        for (String product : allProducts) {
            createPolicy("IT_ADMIN", PolicyTargetType.HERONIX_PRODUCT, product, true,
                    "Default: IT admins have full product access");
        }

        // IT_ADMIN: all third-party categories
        String[] allCategories = {"BROWSER", "EDUCATION", "PRODUCTIVITY", "COMMUNICATION",
                "MULTIMEDIA", "UTILITIES", "DEVELOPMENT", "SCIENCE", "ACCESSIBILITY"};
        for (String category : allCategories) {
            createPolicy("IT_ADMIN", PolicyTargetType.THIRDPARTY_CATEGORY, category, true,
                    "Default: IT admins have full category access");
        }

        // --- TEACHER: Teacher Portal + EdGames only ---
        createPolicy("TEACHER", PolicyTargetType.HERONIX_PRODUCT, "TEACHER", true,
                "Default: Teachers access Teacher Portal");
        createPolicy("TEACHER", PolicyTargetType.HERONIX_PRODUCT, "EDGAMES", true,
                "Default: Teachers access EdGames");

        // TEACHER: Browser and Education categories
        createPolicy("TEACHER", PolicyTargetType.THIRDPARTY_CATEGORY, "BROWSER", true,
                "Default: Teachers access web browsers");
        createPolicy("TEACHER", PolicyTargetType.THIRDPARTY_CATEGORY, "EDUCATION", true,
                "Default: Teachers access educational apps");

        // --- STUDENT: Student Portal + EdGames only ---
        createPolicy("STUDENT", PolicyTargetType.HERONIX_PRODUCT, "STUDENT", true,
                "Default: Students access Student Portal");
        createPolicy("STUDENT", PolicyTargetType.HERONIX_PRODUCT, "EDGAMES", true,
                "Default: Students access EdGames");

        // STUDENT: Education category only
        createPolicy("STUDENT", PolicyTargetType.THIRDPARTY_CATEGORY, "EDUCATION", true,
                "Default: Students access educational apps");

        log.info("Initialized {} default access policies", appAccessPolicyRepository.count());
    }

    private void createPolicy(String roleName, PolicyTargetType targetType, String targetCode,
                               boolean granted, String note) {
        if (!appAccessPolicyRepository.existsByRoleNameAndTargetTypeAndTargetCode(
                roleName, targetType, targetCode)) {
            AppAccessPolicy policy = AppAccessPolicy.builder()
                    .roleName(roleName)
                    .targetType(targetType)
                    .targetCode(targetCode)
                    .accessGranted(granted)
                    .modifiedBy("SYSTEM")
                    .policyNote(note)
                    .build();
            appAccessPolicyRepository.save(policy);
        }
    }
}
