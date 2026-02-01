package com.heronixedu.hub.controller;

import com.heronixedu.hub.model.Product;
import com.heronixedu.hub.model.ThirdPartyApp;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.model.enums.ThirdPartyAppCategory;
import com.heronixedu.hub.service.*;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DashboardController {

    @FXML
    private Label welcomeLabel;

    @FXML
    private Button logoutButton;

    @FXML
    private Button adminPanelButton;

    @FXML
    private Circle serverStatusIndicator;

    @FXML
    private Label serverStatusLabel;

    @FXML
    private FlowPane productTilesContainer;

    @FXML
    private FlowPane thirdPartyTilesContainer;

    @FXML
    private VBox noProductsMessage;

    @FXML
    private VBox heronixProductsSection;

    @FXML
    private VBox thirdPartyAppsSection;

    @FXML
    private HBox categoryFilterBar;

    @FXML
    private ComboBox<String> categoryFilter;

    @FXML
    private TextField searchField;

    @FXML
    private Label thirdPartyCountLabel;

    @FXML
    private Button refreshButton;

    private final AuthenticationService authenticationService;
    private final ProductLauncherService productLauncherService;
    private final PermissionService permissionService;
    private final NetworkConfigService networkConfigService;
    private final ThirdPartyAppService thirdPartyAppService;
    private final ThirdPartyAppLauncherService thirdPartyAppLauncherService;
    private final AuditLogService auditLogService;
    private final AppAccessPolicyService appAccessPolicyService;
    private Runnable onLogout;
    private Runnable onAdminPanel;
    private User currentUser;
    private ScheduledExecutorService statusChecker;
    private Timeline blinkAnimation;
    private List<ThirdPartyApp> allThirdPartyApps = new ArrayList<>();

    // Status colors
    private static final Color COLOR_GREEN = Color.web("#4CAF50");
    private static final Color COLOR_RED = Color.web("#F44336");
    private static final Color COLOR_BLUE = Color.web("#2196F3");
    private static final Color COLOR_GRAY = Color.web("#9E9E9E");

    public DashboardController(AuthenticationService authenticationService,
                               ProductLauncherService productLauncherService,
                               PermissionService permissionService,
                               NetworkConfigService networkConfigService,
                               ThirdPartyAppService thirdPartyAppService,
                               ThirdPartyAppLauncherService thirdPartyAppLauncherService,
                               AuditLogService auditLogService,
                               AppAccessPolicyService appAccessPolicyService) {
        this.authenticationService = authenticationService;
        this.productLauncherService = productLauncherService;
        this.permissionService = permissionService;
        this.networkConfigService = networkConfigService;
        this.thirdPartyAppService = thirdPartyAppService;
        this.thirdPartyAppLauncherService = thirdPartyAppLauncherService;
        this.auditLogService = auditLogService;
        this.appAccessPolicyService = appAccessPolicyService;
    }

    @FXML
    public void initialize() {
        // Initialize status indicator
        if (serverStatusIndicator != null) {
            serverStatusIndicator.setFill(COLOR_GRAY);
        }

        // Initialize category filter
        if (categoryFilter != null) {
            List<String> categories = new ArrayList<>();
            categories.add("All Applications");
            categories.add("Heronix Suite");
            for (ThirdPartyAppCategory cat : ThirdPartyAppCategory.values()) {
                categories.add(cat.getDisplayName());
            }
            categoryFilter.setItems(FXCollections.observableArrayList(categories));
            categoryFilter.setValue("All Applications");
            categoryFilter.setOnAction(e -> filterApplications());
        }

        // Initialize search field
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> filterApplications());
        }
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        welcomeLabel.setText("Welcome, " + user.getFullName());

        // Show admin button only for users who can access admin panel
        boolean canAccessAdmin = permissionService.canAccessAdminPanel(user);
        adminPanelButton.setVisible(canAccessAdmin);
        adminPanelButton.setManaged(canAccessAdmin);

        loadProducts();
        loadThirdPartyApps();
        startServerStatusCheck();
    }

    private void startServerStatusCheck() {
        // Stop any existing checker
        stopServerStatusCheck();

        // Create blink animation for "checking" state
        blinkAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(serverStatusIndicator.fillProperty(), COLOR_BLUE)),
                new KeyFrame(Duration.millis(500), new KeyValue(serverStatusIndicator.fillProperty(), Color.web("#64B5F6"))),
                new KeyFrame(Duration.millis(1000), new KeyValue(serverStatusIndicator.fillProperty(), COLOR_BLUE))
        );
        blinkAnimation.setCycleCount(Timeline.INDEFINITE);

        // Start periodic status check
        statusChecker = Executors.newSingleThreadScheduledExecutor();
        statusChecker.scheduleAtFixedRate(this::checkServerStatus, 0, 30, TimeUnit.SECONDS);
    }

    private void checkServerStatus() {
        Platform.runLater(() -> {
            // Show blinking blue while checking
            serverStatusLabel.setText("Checking...");
            blinkAnimation.play();
        });

        try {
            // Perform connection test
            var config = networkConfigService.getActiveConfig();
            var result = networkConfigService.testConnection(config);

            Platform.runLater(() -> {
                blinkAnimation.stop();

                if (result.isAnyAvailable()) {
                    // Green - connected
                    serverStatusIndicator.setFill(COLOR_GREEN);
                    if (result.isLocalAvailable() && result.isCloudAvailable()) {
                        serverStatusLabel.setText("All Servers Online");
                    } else if (result.isLocalAvailable()) {
                        serverStatusLabel.setText("Local Server");
                    } else {
                        serverStatusLabel.setText("Cloud Server");
                    }
                } else {
                    // Red - offline
                    serverStatusIndicator.setFill(COLOR_RED);
                    serverStatusLabel.setText("Offline");
                }
            });

        } catch (Exception e) {
            log.error("Error checking server status", e);
            Platform.runLater(() -> {
                blinkAnimation.stop();
                serverStatusIndicator.setFill(COLOR_RED);
                serverStatusLabel.setText("Error");
            });
        }
    }

    public void stopServerStatusCheck() {
        if (statusChecker != null && !statusChecker.isShutdown()) {
            statusChecker.shutdown();
        }
        if (blinkAnimation != null) {
            blinkAnimation.stop();
        }
    }

    private void loadProducts() {
        // Update product installation status
        new Thread(() -> {
            productLauncherService.updateProductInstallationStatus();

            // Get products filtered by user role
            List<Product> products;
            if (currentUser != null) {
                products = productLauncherService.getProductsForUser(currentUser);
                log.debug("Loaded {} products for user {} with role {}",
                        products.size(), currentUser.getUsername(), currentUser.getRole());
            } else {
                // Fallback to empty list if no user (shouldn't happen)
                products = List.of();
            }

            Platform.runLater(() -> {
                productTilesContainer.getChildren().clear();

                if (products.isEmpty()) {
                    noProductsMessage.setVisible(true);
                } else {
                    noProductsMessage.setVisible(false);
                    for (Product product : products) {
                        productTilesContainer.getChildren().add(createProductTile(product));
                    }
                }
            });
        }).start();
    }

    private VBox createProductTile(Product product) {
        VBox tile = new VBox(10);
        tile.setAlignment(Pos.CENTER);
        tile.getStyleClass().add("product-tile");
        tile.setPrefSize(200, 150);

        // Product name
        Text productName = new Text(product.getProductName());
        productName.setTextAlignment(TextAlignment.CENTER);
        productName.setWrappingWidth(180);
        productName.getStyleClass().add("product-name");

        // Status indicator
        Text status = new Text(product.getIsInstalled() ? "Installed" : "Not Installed");
        status.getStyleClass().add(product.getIsInstalled() ? "status-installed" : "status-not-installed");

        // Launch button (only show if installed)
        if (product.getIsInstalled()) {
            Button launchButton = new Button("Launch");
            launchButton.getStyleClass().add("launch-button");
            launchButton.setOnAction(e -> launchProduct(product));

            tile.getChildren().addAll(productName, status, launchButton);
        } else {
            tile.getChildren().addAll(productName, status);
            tile.setOpacity(0.6);
        }

        return tile;
    }

    private void launchProduct(Product product) {
        log.info("Launching product: {}", product.getProductCode());

        new Thread(() -> {
            try {
                productLauncherService.launchProduct(product.getProductCode());

                Platform.runLater(() -> {
                    showInfo("Product Launched",
                            product.getProductName() + " has been launched successfully.");
                });

            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    showError("Launch Failed", e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void handleLogout() {
        log.info("User logging out");
        stopServerStatusCheck();

        new Thread(() -> {
            try {
                authenticationService.logout();

                Platform.runLater(() -> {
                    if (onLogout != null) {
                        onLogout.run();
                    }
                });

            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    showError("Logout Failed", e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void handleAdminPanel() {
        log.info("Opening admin panel");
        stopServerStatusCheck();
        if (onAdminPanel != null) {
            onAdminPanel.run();
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void setOnLogout(Runnable callback) {
        this.onLogout = callback;
    }

    public void setOnAdminPanel(Runnable callback) {
        this.onAdminPanel = callback;
    }

    public void refresh() {
        loadProducts();
        loadThirdPartyApps();
        checkServerStatus();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    private void loadThirdPartyApps() {
        new Thread(() -> {
            try {
                // Get installed third-party apps
                List<ThirdPartyApp> installedApps = thirdPartyAppService.getInstalledApps();

                // Filter by role-based access policies
                String userRole = currentUser != null ? currentUser.getRole() : null;
                if (userRole != null && !"SUPERADMIN".equalsIgnoreCase(userRole)) {
                    allThirdPartyApps = appAccessPolicyService.filterAccessibleApps(userRole, installedApps);
                } else {
                    allThirdPartyApps = installedApps;
                }

                Platform.runLater(() -> {
                    displayThirdPartyApps(allThirdPartyApps);
                    updateNoProductsVisibility();
                });
            } catch (Exception e) {
                log.error("Error loading third-party apps", e);
                Platform.runLater(() -> {
                    thirdPartyAppsSection.setVisible(false);
                    thirdPartyAppsSection.setManaged(false);
                });
            }
        }).start();
    }

    private void displayThirdPartyApps(List<ThirdPartyApp> apps) {
        thirdPartyTilesContainer.getChildren().clear();

        if (apps.isEmpty()) {
            thirdPartyAppsSection.setVisible(false);
            thirdPartyAppsSection.setManaged(false);
            thirdPartyCountLabel.setText("");
        } else {
            thirdPartyAppsSection.setVisible(true);
            thirdPartyAppsSection.setManaged(true);
            thirdPartyCountLabel.setText("(" + apps.size() + ")");

            for (ThirdPartyApp app : apps) {
                thirdPartyTilesContainer.getChildren().add(createThirdPartyTile(app));
            }
        }
    }

    private VBox createThirdPartyTile(ThirdPartyApp app) {
        VBox tile = new VBox(8);
        tile.setAlignment(Pos.CENTER);
        tile.getStyleClass().add("product-tile");
        tile.getStyleClass().add("third-party-tile");
        tile.setPrefSize(180, 160);

        // App icon (if available)
        if (app.getIconUrl() != null && !app.getIconUrl().isEmpty()) {
            try {
                ImageView icon = new ImageView();
                icon.setFitWidth(48);
                icon.setFitHeight(48);
                icon.setPreserveRatio(true);

                if (app.getIconUrl().startsWith("http")) {
                    icon.setImage(new Image(app.getIconUrl(), true));
                } else if (new File(app.getIconUrl()).exists()) {
                    icon.setImage(new Image(new File(app.getIconUrl()).toURI().toString()));
                }
                tile.getChildren().add(icon);
            } catch (Exception e) {
                // Skip icon if it can't be loaded
                log.debug("Could not load icon for {}: {}", app.getAppName(), e.getMessage());
            }
        }

        // App name
        Text appName = new Text(app.getAppName());
        appName.setTextAlignment(TextAlignment.CENTER);
        appName.setWrappingWidth(160);
        appName.getStyleClass().add("product-name");

        // Category badge
        Label categoryBadge = new Label(app.getCategory().getDisplayName());
        categoryBadge.getStyleClass().add("category-badge");
        categoryBadge.getStyleClass().add("category-" + app.getCategory().name().toLowerCase());

        // Version info
        Text version = new Text("v" + (app.getCurrentVersion() != null ? app.getCurrentVersion() : "N/A"));
        version.getStyleClass().add("version-text");

        // Launch button
        Button launchButton = new Button("Launch");
        launchButton.getStyleClass().add("launch-button");
        launchButton.setOnAction(e -> launchThirdPartyApp(app));

        tile.getChildren().addAll(appName, categoryBadge, version, launchButton);

        // Tooltip with more info
        Tooltip tooltip = new Tooltip(
                app.getAppName() + "\n" +
                "Publisher: " + (app.getPublisher() != null ? app.getPublisher() : "Unknown") + "\n" +
                "Category: " + app.getCategory().getDisplayName() + "\n" +
                (app.getDescription() != null ? app.getDescription() : "")
        );
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(300);
        Tooltip.install(tile, tooltip);

        return tile;
    }

    private void launchThirdPartyApp(ThirdPartyApp app) {
        log.info("Launching third-party app: {}", app.getAppName());

        new Thread(() -> {
            try {
                thirdPartyAppLauncherService.launchApp(app, currentUser);

                Platform.runLater(() -> {
                    showInfo("Application Launched",
                            app.getAppName() + " has been launched successfully.");
                });

            } catch (Exception e) {
                log.error("Failed to launch {}: {}", app.getAppName(), e.getMessage());
                Platform.runLater(() -> {
                    showError("Launch Failed",
                            "Could not launch " + app.getAppName() + ":\n" + e.getMessage());
                });
            }
        }).start();
    }

    private void filterApplications() {
        String selectedCategory = categoryFilter.getValue();
        String searchText = searchField.getText() != null ? searchField.getText().toLowerCase().trim() : "";

        // Filter Heronix products section visibility
        boolean showHeronix = "All Applications".equals(selectedCategory) || "Heronix Suite".equals(selectedCategory);
        heronixProductsSection.setVisible(showHeronix);
        heronixProductsSection.setManaged(showHeronix);

        // Filter third-party apps
        List<ThirdPartyApp> filteredApps = allThirdPartyApps.stream()
                .filter(app -> {
                    // Category filter
                    if (!"All Applications".equals(selectedCategory) && !"Heronix Suite".equals(selectedCategory)) {
                        if (!app.getCategory().getDisplayName().equals(selectedCategory)) {
                            return false;
                        }
                    }
                    // Search filter
                    if (!searchText.isEmpty()) {
                        boolean matchesName = app.getAppName().toLowerCase().contains(searchText);
                        boolean matchesPublisher = app.getPublisher() != null &&
                                app.getPublisher().toLowerCase().contains(searchText);
                        boolean matchesTags = app.getTags() != null &&
                                app.getTags().toLowerCase().contains(searchText);
                        return matchesName || matchesPublisher || matchesTags;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        displayThirdPartyApps(filteredApps);

        // Also filter Heronix products by search if visible
        if (showHeronix && !searchText.isEmpty()) {
            filterHeronixProducts(searchText);
        } else if (showHeronix) {
            loadProducts(); // Reload all products
        }

        updateNoProductsVisibility();
    }

    private void filterHeronixProducts(String searchText) {
        // Get products filtered by user role first
        List<Product> products;
        if (currentUser != null) {
            products = productLauncherService.getProductsForUser(currentUser);
        } else {
            products = List.of();
        }

        productTilesContainer.getChildren().clear();

        List<Product> filtered = products.stream()
                .filter(p -> p.getProductName().toLowerCase().contains(searchText) ||
                        (p.getDescription() != null && p.getDescription().toLowerCase().contains(searchText)))
                .collect(Collectors.toList());

        for (Product product : filtered) {
            productTilesContainer.getChildren().add(createProductTile(product));
        }
    }

    private void updateNoProductsVisibility() {
        boolean hasHeronixProducts = heronixProductsSection.isVisible() &&
                !productTilesContainer.getChildren().isEmpty();
        boolean hasThirdPartyApps = thirdPartyAppsSection.isVisible() &&
                !thirdPartyTilesContainer.getChildren().isEmpty();

        noProductsMessage.setVisible(!hasHeronixProducts && !hasThirdPartyApps);
        noProductsMessage.setManaged(!hasHeronixProducts && !hasThirdPartyApps);
    }
}
