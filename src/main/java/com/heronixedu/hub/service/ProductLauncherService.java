package com.heronixedu.hub.service;

import com.heronixedu.hub.model.Product;
import com.heronixedu.hub.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductLauncherService {

    private final ProductRepository productRepository;
    private final AppAccessPolicyService appAccessPolicyService;

    // Base directory where Heronix projects are located
    @Value("${heronix.products.base-path:#{null}}")
    private String configuredBasePath;

    // Known Heronix project prefixes to look for
    private static final List<String> HERONIX_PROJECT_PREFIXES = List.of(
            "Heronix-", "heronix-"
    );

    // Category mappings for known products
    private static final Map<String, String> PRODUCT_CATEGORIES = Map.of(
            "SIS", "Administration",
            "Student", "Student Portal",
            "Teacher", "Teaching Tools",
            "Guardian", "Security",
            "GuardianMonitor", "Security",
            "Talk", "Communication",
            "TalkModule", "Communication",
            "Scheduler", "Administration",
            "EdGames", "Education",
            "Hub", "System"
    );

    // Product descriptions
    private static final Map<String, String> PRODUCT_DESCRIPTIONS = Map.ofEntries(
            Map.entry("SIS", "Student Information System - Manage student records, grades, and enrollment"),
            Map.entry("Student", "Student Portal - Access grades, assignments, and school resources"),
            Map.entry("Teacher", "Teacher Dashboard - Manage classes, grades, and student progress"),
            Map.entry("Guardian", "Guardian Security - Protect student data and monitor system security"),
            Map.entry("GuardianMonitor", "Guardian Monitor - Real-time security monitoring dashboard"),
            Map.entry("Talk", "Heronix Talk - Secure messaging and communication platform"),
            Map.entry("TalkModule", "Talk Module - Communication integration module"),
            Map.entry("Scheduler", "Class Scheduler - Schedule classes and manage timetables"),
            Map.entry("SchedulerV2", "Class Scheduler V2 - Advanced scheduling with conflict detection"),
            Map.entry("EdGames", "Educational Games - Interactive learning games for students"),
            Map.entry("Hub", "Heronix Hub - Central application launcher and management")
    );

    // Role-based access configuration for products
    // Key: product code, Value: allowed roles (null means all roles, empty string means system only)
    private static final Map<String, String> PRODUCT_ALLOWED_ROLES = Map.ofEntries(
            // Admin/IT only products
            Map.entry("SIS", "SUPERADMIN,IT_ADMIN"),
            Map.entry("SISSERVER", "SUPERADMIN,IT_ADMIN"),
            Map.entry("GUARDIAN", "SUPERADMIN,IT_ADMIN"),
            Map.entry("GUARDIANMONITOR", "SUPERADMIN,IT_ADMIN"),
            Map.entry("HUB", "SUPERADMIN,IT_ADMIN"),

            // Teacher and Admin products
            Map.entry("TEACHER", "SUPERADMIN,IT_ADMIN,TEACHER"),
            Map.entry("SCHEDULERV2", "SUPERADMIN,IT_ADMIN,TEACHER"),
            Map.entry("SCHEDULER", "SUPERADMIN,IT_ADMIN,TEACHER"),

            // Student accessible products (teachers and admins can also access)
            Map.entry("STUDENT", "SUPERADMIN,IT_ADMIN,TEACHER,STUDENT"),
            Map.entry("EDGAMES", "SUPERADMIN,IT_ADMIN,TEACHER,STUDENT"),
            Map.entry("TALK", "SUPERADMIN,IT_ADMIN,TEACHER,STUDENT"),
            Map.entry("TALKMODULE", "SUPERADMIN,IT_ADMIN,TEACHER,STUDENT")
    );

    // Products that are system/admin-only applications
    private static final Set<String> SYSTEM_APPS = Set.of(
            "SIS", "SISSERVER", "GUARDIAN", "GUARDIANMONITOR", "HUB"
    );

    /**
     * Launch a product by its product code.
     * Supports two launch modes:
     * 1. Production: java -jar target/*.jar (preferred, no Maven required)
     * 2. Development: mvn javafx:run (fallback, requires Maven + JDK)
     */
    public void launchProduct(String productCode) {
        try {
            log.info("Launching product: {}", productCode);

            Product product = productRepository.findByProductCode(productCode)
                    .orElseThrow(() -> new RuntimeException("Product not found: " + productCode));

            // Get project path
            String projectPath = product.getExecutablePath();
            File projectDir = new File(projectPath);

            // Check if project directory exists
            if (!projectDir.exists() || !projectDir.isDirectory()) {
                log.error("Product project not found: {}", projectPath);
                throw new RuntimeException("Product not installed: " + product.getProductName() +
                        "\nExpected location: " + projectPath);
            }

            // SSO is handled via the shared token file at ~/.heronix/auth/token.jwt
            // Child products check for this file on startup to enable auto-login
            ProcessBuilder processBuilder;
            String os = System.getProperty("os.name").toLowerCase();

            // Try production mode first: look for executable JAR in target/
            File jarFile = findExecutableJar(projectDir);

            if (jarFile != null) {
                // Production mode: launch via java -jar
                log.info("Launching {} in production mode (JAR: {})", productCode, jarFile.getName());

                if (os.contains("win")) {
                    processBuilder = new ProcessBuilder(
                            "cmd", "/c", "java", "-jar", jarFile.getAbsolutePath()
                    );
                } else {
                    processBuilder = new ProcessBuilder(
                            "java", "-jar", jarFile.getAbsolutePath()
                    );
                }
            } else {
                // Development mode: fall back to mvn javafx:run
                File pomFile = new File(projectDir, "pom.xml");
                if (!pomFile.exists()) {
                    throw new RuntimeException("Product not installed: " + product.getProductName() +
                            "\nNo JAR found and no pom.xml in: " + projectPath);
                }

                log.info("Launching {} in development mode (mvn javafx:run)", productCode);

                if (os.contains("win")) {
                    processBuilder = new ProcessBuilder(
                            "cmd", "/c", "mvn", "javafx:run"
                    );
                } else {
                    processBuilder = new ProcessBuilder(
                            "mvn", "javafx:run"
                    );
                }
            }

            // Set working directory to project location
            processBuilder.directory(projectDir);

            // Redirect output to separate console
            processBuilder.inheritIO();

            // Launch the process
            Process process = processBuilder.start();

            // Update last_launched timestamp
            product.setLastLaunched(LocalDateTime.now());
            product.setIsInstalled(true);
            productRepository.save(product);

            log.info("Product launched successfully: {} (PID: {})",
                    product.getProductName(), process.pid());

        } catch (IOException e) {
            log.error("Error launching product: {}", productCode, e);
            throw new RuntimeException("Failed to launch product: " + e.getMessage(), e);
        }
    }

    /**
     * Find an executable Spring Boot JAR in the project's target/ directory.
     * Skips -sources, -javadoc, and original (non-fat) JARs.
     */
    private File findExecutableJar(File projectDir) {
        File targetDir = new File(projectDir, "target");
        if (!targetDir.exists() || !targetDir.isDirectory()) {
            return null;
        }

        File[] jars = targetDir.listFiles((dir, name) ->
                name.endsWith(".jar")
                        && !name.endsWith("-sources.jar")
                        && !name.endsWith("-javadoc.jar")
                        && !name.contains(".original"));

        if (jars == null || jars.length == 0) {
            return null;
        }

        // Return the largest JAR (the fat/uber JAR with all dependencies)
        File best = null;
        for (File jar : jars) {
            if (best == null || jar.length() > best.length()) {
                best = jar;
            }
        }

        // Sanity check: a Spring Boot fat JAR should be at least 5MB
        if (best != null && best.length() < 5 * 1024 * 1024) {
            log.debug("JAR {} is too small ({}KB), likely not a fat JAR", best.getName(), best.length() / 1024);
            return null;
        }

        return best;
    }

    /**
     * Get all products (for displaying tiles)
     */
    public List<Product> getAllProducts() {
        return productRepository.findAllByOrderByProductNameAsc();
    }

    /**
     * Get only installed products
     */
    public List<Product> getInstalledProducts() {
        return productRepository.findAllByIsInstalledTrue();
    }

    /**
     * Get products accessible by a specific user role.
     * Uses AppAccessPolicyService for configurable policy-based access control.
     * @param userRole the role name (e.g., "TEACHER", "STUDENT", "IT_ADMIN", "SUPERADMIN")
     * @return list of products the user can access
     */
    public List<Product> getProductsForRole(String userRole) {
        // Use the policy service if policies have been initialized
        if (appAccessPolicyService.hasPolicies()) {
            return appAccessPolicyService.getAccessibleProducts(userRole);
        }

        // Fallback to Product.isAccessibleByRole if no policies exist yet
        List<Product> allProducts = productRepository.findAllByIsInstalledTrue();
        return allProducts.stream()
                .filter(product -> product.isAccessibleByRole(userRole))
                .toList();
    }

    /**
     * Get products accessible by a specific user.
     * @param user the user to check access for
     * @return list of products the user can access
     */
    public List<Product> getProductsForUser(com.heronixedu.hub.model.User user) {
        if (user == null) {
            return List.of();
        }
        String role = user.getRole();
        return getProductsForRole(role);
    }

    /**
     * Check if a product is installed (project directory with pom.xml exists)
     */
    public boolean isProductInstalled(String productCode) {
        return productRepository.findByProductCode(productCode)
                .map(product -> {
                    File projectDir = new File(product.getExecutablePath());
                    boolean exists = projectDir.exists() && projectDir.isDirectory()
                            && (new File(projectDir, "pom.xml").exists() || findExecutableJar(projectDir) != null);

                    // Update installation status if it changed
                    if (product.getIsInstalled() != exists) {
                        product.setIsInstalled(exists);
                        productRepository.save(product);
                    }

                    return exists;
                })
                .orElse(false);
    }

    /**
     * Update product installation status for all products
     */
    public void updateProductInstallationStatus() {
        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            File projectDir = new File(product.getExecutablePath());
            boolean exists = projectDir.exists() && projectDir.isDirectory()
                    && (new File(projectDir, "pom.xml").exists() || findExecutableJar(projectDir) != null);

            if (product.getIsInstalled() != exists) {
                product.setIsInstalled(exists);
                productRepository.save(product);
                log.info("Updated installation status for {}: {}",
                        product.getProductCode(), exists);
            }
        }
    }

    /**
     * Auto-discover Heronix products on startup.
     */
    @PostConstruct
    public void discoverProducts() {
        log.info("Starting Heronix product auto-discovery...");

        // First, clean up legacy/duplicate products
        cleanupLegacyProducts();

        // Determine base path
        String basePath = getHeronixBasePath();
        if (basePath == null) {
            log.warn("Could not determine Heronix base path. Skipping auto-discovery.");
            return;
        }

        File baseDir = new File(basePath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            log.warn("Heronix base path does not exist: {}", basePath);
            return;
        }

        // Scan for Heronix projects
        File[] subdirs = baseDir.listFiles(File::isDirectory);
        if (subdirs == null) {
            return;
        }

        int discovered = 0;
        for (File subdir : subdirs) {
            if (isHeronixProject(subdir)) {
                if (registerProduct(subdir)) {
                    discovered++;
                }
            }
        }

        log.info("Product discovery complete. Found {} new products.", discovered);

        // Update installation status for all products
        updateProductInstallationStatus();
    }

    /**
     * Clean up legacy hardcoded products and duplicates.
     * This removes products that were manually created in DataInitializer
     * and are now superseded by auto-discovery.
     */
    private void cleanupLegacyProducts() {
        // Legacy product codes that were hardcoded in DataInitializer
        Set<String> legacyProductCodes = Set.of(
                "TALK_MODULE",       // Legacy: auto-discovered as TALKMODULE
                "MESSAGING_SYSTEM",  // Legacy: never matched a real project
                "HERONIX_SIS",       // Legacy: auto-discovered as SIS
                "HERONIX_STUDENT",   // Legacy: auto-discovered as STUDENT
                "HERONIX_TEACHER",   // Legacy: auto-discovered as TEACHER
                "HERONIX_HUB",       // Legacy: auto-discovered as HUB
                "CLASS_SCHEDULER",   // Legacy: auto-discovered as SCHEDULERV2
                "ADMIN_PORTAL"       // Legacy: never matched a real project
        );

        int removed = 0;
        for (String legacyCode : legacyProductCodes) {
            Optional<Product> legacyProduct = productRepository.findByProductCode(legacyCode);
            if (legacyProduct.isPresent()) {
                productRepository.delete(legacyProduct.get());
                log.info("Removed legacy product: {}", legacyCode);
                removed++;
            }
        }

        // Also remove products that don't have a valid executable path (orphaned entries)
        List<Product> allProducts = productRepository.findAll();
        for (Product product : allProducts) {
            if (product.getExecutablePath() == null || product.getExecutablePath().isEmpty()) {
                productRepository.delete(product);
                log.info("Removed orphaned product with no path: {}", product.getProductCode());
                removed++;
                continue;
            }

            // Check for products with non-existent paths
            File projectDir = new File(product.getExecutablePath());
            if (!projectDir.exists()) {
                // Mark as not installed rather than deleting (in case path is temporarily unavailable)
                product.setIsInstalled(false);
                productRepository.save(product);
            }
        }

        // Remove duplicate products (same name but different codes)
        removeDuplicatesByName();

        if (removed > 0) {
            log.info("Cleanup complete. Removed {} legacy/orphaned products.", removed);
        }
    }

    /**
     * Remove duplicate products that have the same effective name but different codes.
     */
    private void removeDuplicatesByName() {
        List<Product> allProducts = productRepository.findAll();
        Map<String, Product> seenNames = new HashMap<>();
        List<Product> toRemove = new ArrayList<>();

        for (Product product : allProducts) {
            // Normalize the product name for comparison
            String normalizedName = product.getProductName()
                    .toLowerCase()
                    .replaceAll("\\s+", "")
                    .replaceAll("-", "")
                    .replaceAll("_", "");

            if (seenNames.containsKey(normalizedName)) {
                Product existing = seenNames.get(normalizedName);
                // Keep the one that has a valid path, or the newer one
                File existingPath = new File(existing.getExecutablePath() != null ? existing.getExecutablePath() : "");
                File currentPath = new File(product.getExecutablePath() != null ? product.getExecutablePath() : "");

                if (currentPath.exists() && !existingPath.exists()) {
                    // Current is valid, remove existing
                    toRemove.add(existing);
                    seenNames.put(normalizedName, product);
                    log.info("Removing duplicate (invalid path): {} in favor of {}",
                            existing.getProductCode(), product.getProductCode());
                } else {
                    // Existing is valid or both invalid, remove current
                    toRemove.add(product);
                    log.info("Removing duplicate: {} in favor of {}",
                            product.getProductCode(), existing.getProductCode());
                }
            } else {
                seenNames.put(normalizedName, product);
            }
        }

        for (Product product : toRemove) {
            productRepository.delete(product);
        }
    }

    /**
     * Scheduled periodic scan for new products (every 5 minutes).
     */
    @Scheduled(fixedRate = 300000)
    public void periodicProductScan() {
        discoverProducts();
    }

    /**
     * Get the base path where Heronix projects are located.
     */
    private String getHeronixBasePath() {
        // 1. Use configured path if available
        if (configuredBasePath != null && !configuredBasePath.isEmpty()) {
            return configuredBasePath;
        }

        // 2. Try to detect from Hub's own location
        String hubPath = System.getProperty("user.dir");
        File hubDir = new File(hubPath);

        // If we're in the Hub project, parent should be the Heronix root
        if (hubDir.getName().contains("Heronix-Hub") || hubDir.getName().contains("heronix-hub")) {
            return hubDir.getParent();
        }

        // 3. Check if current directory IS the Heronix root (contains multiple Heronix- folders)
        File[] children = hubDir.listFiles(f -> f.isDirectory() &&
                (f.getName().startsWith("Heronix-") || f.getName().startsWith("heronix-")));
        if (children != null && children.length > 1) {
            return hubPath;
        }

        // 4. Check common development paths
        String userHome = System.getProperty("user.home");
        String[] commonPaths = {
                userHome + File.separator + "Heronix",
                userHome + File.separator + "Projects" + File.separator + "Heronix",
                userHome + File.separator + "IdeaProjects" + File.separator + "Heronix",
                "C:" + File.separator + "Heronix",
                "D:" + File.separator + "Heronix"
        };

        for (String path : commonPaths) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                return path;
            }
        }

        return null;
    }

    /**
     * Check if a directory is a Heronix project.
     * Accepts directories with pom.xml (source) or target/*.jar (built).
     */
    private boolean isHeronixProject(File dir) {
        String name = dir.getName();

        // Must start with Heronix- prefix
        boolean hasPrefix = false;
        for (String prefix : HERONIX_PROJECT_PREFIXES) {
            if (name.startsWith(prefix)) {
                hasPrefix = true;
                break;
            }
        }
        if (!hasPrefix) {
            return false;
        }

        // Must have either pom.xml (source project) or an executable JAR (built project)
        File pomFile = new File(dir, "pom.xml");
        if (pomFile.exists()) {
            return true;
        }

        // Check for pre-built JAR
        return findExecutableJar(dir) != null;
    }

    /**
     * Register a discovered product in the database.
     */
    private boolean registerProduct(File projectDir) {
        String dirName = projectDir.getName();

        // Extract product code from directory name (e.g., "Heronix-SIS" -> "SIS")
        String productCode = dirName.replaceFirst("(?i)heronix-", "").toUpperCase();

        // Skip if already registered
        if (productRepository.findByProductCode(productCode).isPresent()) {
            // Update role restrictions if needed
            updateProductRoleRestrictions(productCode);
            return false;
        }

        // Create product name from code
        String productName = "Heronix " + formatProductName(productCode);

        // Get category and description
        String category = PRODUCT_CATEGORIES.getOrDefault(productCode, "Other");
        String description = PRODUCT_DESCRIPTIONS.get(productCode);
        if (description == null) {
            description = "Heronix " + formatProductName(productCode) + " module";
        }

        // Try to read version from pom.xml
        String version = readVersionFromPom(projectDir);

        // Create and save the product
        Product product = new Product();
        product.setProductCode(productCode);
        product.setProductName(productName);
        product.setExecutablePath(projectDir.getAbsolutePath());
        product.setCategory(category);
        product.setDescription(description);
        product.setCurrentVersion(version != null ? version : "1.0.0");
        product.setIsInstalled(true);
        product.setRequiresAdmin(SYSTEM_APPS.contains(productCode));

        // Set role-based access restrictions
        String allowedRoles = PRODUCT_ALLOWED_ROLES.get(productCode);
        if (allowedRoles != null) {
            product.setAllowedRoles(allowedRoles);
        }
        product.setIsSystemApp(SYSTEM_APPS.contains(productCode));

        productRepository.save(product);
        log.info("Registered new Heronix product: {} at {} (roles: {})",
                productName, projectDir.getAbsolutePath(), allowedRoles != null ? allowedRoles : "ALL");

        return true;
    }

    /**
     * Update role restrictions for an existing product if needed.
     */
    private void updateProductRoleRestrictions(String productCode) {
        productRepository.findByProductCode(productCode).ifPresent(product -> {
            boolean updated = false;

            // Set allowed roles if not already set
            if (product.getAllowedRoles() == null) {
                String allowedRoles = PRODUCT_ALLOWED_ROLES.get(productCode);
                if (allowedRoles != null) {
                    product.setAllowedRoles(allowedRoles);
                    updated = true;
                }
            }

            // Set system app flag if not already set
            if (product.getIsSystemApp() == null || !product.getIsSystemApp()) {
                if (SYSTEM_APPS.contains(productCode)) {
                    product.setIsSystemApp(true);
                    product.setRequiresAdmin(true);
                    updated = true;
                }
            }

            if (updated) {
                productRepository.save(product);
                log.info("Updated role restrictions for product: {}", productCode);
            }
        });
    }

    /**
     * Format product code into a readable name.
     */
    private String formatProductName(String code) {
        // Handle special cases
        if (code.equals("SIS")) return "SIS";
        if (code.equals("EDGAMES")) return "EdGames";

        // Convert camelCase or dash-separated to Title Case
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : code.toCharArray()) {
            if (c == '-' || c == '_') {
                result.append(' ');
                capitalizeNext = true;
            } else if (Character.isUpperCase(c) && result.length() > 0 && !capitalizeNext) {
                result.append(' ').append(c);
                capitalizeNext = false;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }

    /**
     * Read version from pom.xml file.
     */
    private String readVersionFromPom(File projectDir) {
        File pomFile = new File(projectDir, "pom.xml");
        if (!pomFile.exists()) {
            return null;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile);

            NodeList versionNodes = doc.getElementsByTagName("version");
            if (versionNodes.getLength() > 0) {
                return versionNodes.item(0).getTextContent();
            }
        } catch (Exception e) {
            log.debug("Could not read version from pom.xml: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Manually trigger product discovery (for admin use).
     */
    public int manualDiscovery() {
        long countBefore = productRepository.count();
        discoverProducts();
        long countAfter = productRepository.count();
        return (int) (countAfter - countBefore);
    }

    /**
     * Get the detected base path for display purposes.
     */
    public String getDetectedBasePath() {
        return getHeronixBasePath();
    }

    /**
     * Manually trigger cleanup of legacy and duplicate products (for admin use).
     * @return number of products removed
     */
    public int manualCleanup() {
        long countBefore = productRepository.count();
        cleanupLegacyProducts();
        long countAfter = productRepository.count();
        int removed = (int) (countBefore - countAfter);
        log.info("Manual cleanup complete. Removed {} products.", removed);
        return removed;
    }

    /**
     * Get all products currently in database (including uninstalled) for debugging.
     */
    public List<Product> getAllProductsIncludingUninstalled() {
        return productRepository.findAll();
    }
}
