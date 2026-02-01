package com.heronixedu.hub.controller;

import com.heronixedu.hub.model.*;
import com.heronixedu.hub.model.enums.AuditAction;
import com.heronixedu.hub.model.enums.InstallerType;
import com.heronixedu.hub.model.enums.PermissionType;
import com.heronixedu.hub.model.enums.ServerType;
import com.heronixedu.hub.model.enums.ThirdPartyAppCategory;
import com.heronixedu.hub.model.enums.UpdatePolicy;
import com.heronixedu.hub.service.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class AdminPanelController {

    // Tabs
    @FXML private TabPane adminTabPane;
    @FXML private Tab usersTab;
    @FXML private Tab networkTab;
    @FXML private Tab productsTab;
    @FXML private Tab softwareCatalogTab;
    @FXML private Tab devicesTab;
    @FXML private Tab logsTab;
    @FXML private Tab statusTab;

    // Header
    @FXML private Label userRoleLabel;
    @FXML private Button backButton;

    // Users Tab
    @FXML private TextField userSearchField;
    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, Long> userIdColumn;
    @FXML private TableColumn<User, String> usernameColumn;
    @FXML private TableColumn<User, String> fullNameColumn;
    @FXML private TableColumn<User, String> emailColumn;
    @FXML private TableColumn<User, String> userRoleColumn;
    @FXML private TableColumn<User, Boolean> activeColumn;
    @FXML private TableColumn<User, Void> userActionsColumn;

    // Network Tab
    @FXML private ComboBox<ServerType> serverTypeCombo;
    @FXML private TextField localServerPathField;
    @FXML private TextField cloudServerUrlField;
    @FXML private Label connectionStatusLabel;
    @FXML private CheckBox proxyEnabledCheckbox;
    @FXML private ComboBox<String> proxyTypeCombo;
    @FXML private TextField proxyHostField;
    @FXML private TextField proxyPortField;
    @FXML private TextField proxyUsernameField;
    @FXML private PasswordField proxyPasswordField;
    @FXML private CheckBox customDnsCheckbox;
    @FXML private TextField primaryDnsField;
    @FXML private TextField secondaryDnsField;
    @FXML private CheckBox sslEnabledCheckbox;
    @FXML private TextField sslCertPathField;
    @FXML private CheckBox sslVerifyHostnameCheckbox;
    @FXML private TextArea firewallInfoArea;

    // Products Tab
    @FXML private FlowPane productCardsPane;
    @FXML private Label productsStatusLabel;

    // Software Catalog Tab
    @FXML private TextField softwareSearchField;
    @FXML private ComboBox<ThirdPartyAppCategory> softwareCategoryFilter;
    @FXML private CheckBox showApprovedOnlyCheckbox;
    @FXML private TableView<ThirdPartyApp> softwareTable;
    @FXML private TableColumn<ThirdPartyApp, String> swNameColumn;
    @FXML private TableColumn<ThirdPartyApp, String> swPublisherColumn;
    @FXML private TableColumn<ThirdPartyApp, ThirdPartyAppCategory> swCategoryColumn;
    @FXML private TableColumn<ThirdPartyApp, String> swVersionColumn;
    @FXML private TableColumn<ThirdPartyApp, InstallerType> swInstallerTypeColumn;
    @FXML private TableColumn<ThirdPartyApp, Boolean> swApprovedColumn;
    @FXML private TableColumn<ThirdPartyApp, Boolean> swInstalledColumn;
    @FXML private TableColumn<ThirdPartyApp, Void> swActionsColumn;
    @FXML private Label totalSoftwareLabel;
    @FXML private Label approvedSoftwareLabel;
    @FXML private Label installedSoftwareLabel;
    @FXML private Label pendingSoftwareLabel;
    @FXML private TitledPane pendingApprovalPane;
    @FXML private FlowPane pendingAppsPane;
    @FXML private Label updatesAvailableLabel;
    @FXML private TitledPane pendingUpdatesPane;
    @FXML private FlowPane pendingUpdatesFlowPane;
    @FXML private Button checkAllUpdatesBtn;

    // Devices Tab
    @FXML private TableView<SisApiClient.DeviceSummary> devicesTable;
    @FXML private TableColumn<SisApiClient.DeviceSummary, String> deviceNameColumn;
    @FXML private TableColumn<SisApiClient.DeviceSummary, String> deviceIdColumn;
    @FXML private TableColumn<SisApiClient.DeviceSummary, String> deviceMacColumn;
    @FXML private TableColumn<SisApiClient.DeviceSummary, String> deviceOsColumn;
    @FXML private TableColumn<SisApiClient.DeviceSummary, String> deviceAccountColumn;
    @FXML private TableColumn<SisApiClient.DeviceSummary, String> deviceStatusColumn;
    @FXML private TableColumn<SisApiClient.DeviceSummary, String> deviceRegisteredAtColumn;
    @FXML private TableColumn<SisApiClient.DeviceSummary, Void> deviceActionsColumn;

    // Logs Tab
    @FXML private TextField logSearchField;
    @FXML private ComboBox<AuditAction> actionFilterCombo;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private TableView<AuditLog> logsTable;
    @FXML private TableColumn<AuditLog, String> logTimestampColumn;
    @FXML private TableColumn<AuditLog, String> logUsernameColumn;
    @FXML private TableColumn<AuditLog, AuditAction> logActionColumn;
    @FXML private TableColumn<AuditLog, String> logEntityColumn;
    @FXML private TableColumn<AuditLog, Boolean> logSuccessColumn;
    @FXML private TableColumn<AuditLog, String> logDetailsColumn;
    @FXML private Label pageInfoLabel;

    // Status Tab
    @FXML private Label dbStatusLabel;
    @FXML private Label localServerStatusLabel;
    @FXML private Label cloudServerStatusLabel;
    @FXML private Label totalUsersLabel;
    @FXML private Label totalProductsLabel;
    @FXML private Label installedProductsLabel;
    @FXML private Label hubVersionLabel;
    @FXML private Label javaVersionLabel;
    @FXML private Label osLabel;
    @FXML private ProgressBar memoryProgressBar;
    @FXML private Label memoryLabel;

    // Services
    @Autowired private PermissionService permissionService;
    @Autowired private UserManagementService userManagementService;
    @Autowired private NetworkConfigService networkConfigService;
    @Autowired private ProductLauncherService productLauncherService;
    @Autowired private DeploymentService deploymentService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private SystemStatusService systemStatusService;
    @Autowired private ThirdPartyAppService thirdPartyAppService;
    @Autowired private ThirdPartyInstallerService thirdPartyInstallerService;
    @Autowired private AppUpdateService appUpdateService;
    @Autowired private DeviceApprovalService deviceApprovalService;
    @Autowired private AuthenticationService authenticationService;

    private User currentUser;
    private Runnable onBackToDashboard;
    private int currentPage = 0;
    private static final int PAGE_SIZE = 50;

    @FXML
    public void initialize() {
        setupUsersTable();
        setupNetworkForm();
        setupDevicesTable();
        setupLogsTable();
        setupActionFilter();
        setupSoftwareCatalog();
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        userRoleLabel.setText("Logged in as: " + user.getFullName() + " (" + user.getRole() + ")");
        configureTabsForUser();
        loadInitialData();
    }

    public void setOnBackToDashboard(Runnable callback) {
        this.onBackToDashboard = callback;
    }

    private void configureTabsForUser() {
        usersTab.setDisable(!permissionService.canManageUsers(currentUser));
        networkTab.setDisable(!permissionService.canConfigureNetwork(currentUser));
        productsTab.setDisable(!permissionService.canInstall(currentUser));
        softwareCatalogTab.setDisable(!permissionService.hasPermission(currentUser, PermissionType.CAN_VIEW_SOFTWARE_CATALOG));
        devicesTab.setDisable(!permissionService.canManageDevices(currentUser));
        logsTab.setDisable(!permissionService.canViewLogs(currentUser));
        // Status tab is always visible for admins
    }

    private void loadInitialData() {
        handleRefreshUsers();
        loadNetworkConfig();
        handleRefreshProducts();
        handleRefreshSoftware();
        handleRefreshDevices();
        handleSearchLogs();
        handleRefreshStatus();
    }

    // ========== Users Tab ==========

    private void setupUsersTable() {
        userIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        fullNameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        userRoleColumn.setCellValueFactory(new PropertyValueFactory<>("role"));
        activeColumn.setCellValueFactory(new PropertyValueFactory<>("isActive"));

        // Actions column
        userActionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox buttons = new HBox(5, editBtn, deleteBtn);

            {
                editBtn.setOnAction(e -> handleEditUser(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDeleteUser(getTableView().getItems().get(getIndex())));
                deleteBtn.getStyleClass().add("danger-button");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : buttons);
            }
        });
    }

    @FXML
    private void handleRefreshUsers() {
        List<User> users = userManagementService.getAllUsers();
        usersTable.setItems(FXCollections.observableArrayList(users));
    }

    @FXML
    private void handleAddUser() {
        showUserDialog(null);
    }

    private void handleEditUser(User user) {
        showUserDialog(user);
    }

    private void handleDeleteUser(User user) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete User");
        confirm.setContentText("Are you sure you want to delete user: " + user.getUsername() + "?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    userManagementService.deleteUser(user.getId(), currentUser);
                    handleRefreshUsers();
                    showInfo("User deleted successfully");
                } catch (Exception e) {
                    showError("Failed to delete user: " + e.getMessage());
                }
            }
        });
    }

    private void showUserDialog(User user) {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle(user == null ? "Add User" : "Edit User");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField usernameField = new TextField(user != null ? user.getUsername() : "");
        usernameField.setDisable(user != null);
        PasswordField passwordField = new PasswordField();
        TextField fullNameField = new TextField(user != null ? user.getFullName() : "");
        TextField emailField = new TextField(user != null ? user.getEmail() : "");
        ComboBox<Role> roleCombo = new ComboBox<>(FXCollections.observableArrayList(userManagementService.getAllRoles()));
        CheckBox activeCheck = new CheckBox();
        activeCheck.setSelected(user == null || user.getIsActive());

        if (user != null && user.getRoleEntity() != null) {
            roleCombo.setValue(user.getRoleEntity());
        }

        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);
        grid.add(new Label(user == null ? "" : "(Leave blank to keep current)"), 1, 2);
        grid.add(new Label("Full Name:"), 0, 3);
        grid.add(fullNameField, 1, 3);
        grid.add(new Label("Email:"), 0, 4);
        grid.add(emailField, 1, 4);
        grid.add(new Label("Role:"), 0, 5);
        grid.add(roleCombo, 1, 5);
        grid.add(new Label("Active:"), 0, 6);
        grid.add(activeCheck, 1, 6);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                try {
                    if (user == null) {
                        // Create new user
                        return userManagementService.createUser(
                                usernameField.getText(),
                                passwordField.getText(),
                                fullNameField.getText(),
                                emailField.getText(),
                                roleCombo.getValue().getId(),
                                currentUser
                        );
                    } else {
                        // Update existing user
                        if (!passwordField.getText().isEmpty()) {
                            userManagementService.changePassword(user.getId(), passwordField.getText(), currentUser);
                        }
                        return userManagementService.updateUser(
                                user.getId(),
                                fullNameField.getText(),
                                emailField.getText(),
                                roleCombo.getValue().getId(),
                                activeCheck.isSelected(),
                                currentUser
                        );
                    }
                } catch (Exception e) {
                    showError("Failed to save user: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            handleRefreshUsers();
            showInfo("User saved successfully");
        });
    }

    // ========== Network Tab ==========

    private void setupNetworkForm() {
        serverTypeCombo.setItems(FXCollections.observableArrayList(ServerType.values()));
        proxyTypeCombo.setItems(FXCollections.observableArrayList("HTTP", "SOCKS4", "SOCKS5"));
    }

    private void loadNetworkConfig() {
        NetworkConfig config = networkConfigService.getActiveConfig();

        serverTypeCombo.setValue(config.getServerType());
        localServerPathField.setText(config.getLocalServerPath() != null ? config.getLocalServerPath() : "");
        cloudServerUrlField.setText(config.getCloudServerUrl() != null ? config.getCloudServerUrl() : "");

        proxyEnabledCheckbox.setSelected(config.getProxyEnabled());
        proxyTypeCombo.setValue(config.getProxyType());
        proxyHostField.setText(config.getProxyHost() != null ? config.getProxyHost() : "");
        proxyPortField.setText(config.getProxyPort() != null ? config.getProxyPort().toString() : "");
        proxyUsernameField.setText(config.getProxyUsername() != null ? config.getProxyUsername() : "");
        proxyPasswordField.setText(config.getProxyPassword() != null ? config.getProxyPassword() : "");

        customDnsCheckbox.setSelected(config.getCustomDnsEnabled());
        primaryDnsField.setText(config.getPrimaryDns() != null ? config.getPrimaryDns() : "");
        secondaryDnsField.setText(config.getSecondaryDns() != null ? config.getSecondaryDns() : "");

        sslEnabledCheckbox.setSelected(config.getSslEnabled());
        sslCertPathField.setText(config.getSslCertPath() != null ? config.getSslCertPath() : "");
        sslVerifyHostnameCheckbox.setSelected(config.getSslVerifyHostname());
    }

    @FXML
    private void handleBrowseLocal() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Local Server Directory");
        File dir = chooser.showDialog(localServerPathField.getScene().getWindow());
        if (dir != null) {
            localServerPathField.setText(dir.getAbsolutePath());
        }
    }

    @FXML
    private void handleBrowseCert() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select SSL Certificate");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Certificates", "*.pem", "*.crt", "*.cer"));
        File file = chooser.showOpenDialog(sslCertPathField.getScene().getWindow());
        if (file != null) {
            sslCertPathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void handleTestConnection() {
        connectionStatusLabel.setText("Testing...");
        connectionStatusLabel.setStyle("-fx-text-fill: #666;");

        Task<NetworkConfigService.ServerConnectionResult> task = new Task<>() {
            @Override
            protected NetworkConfigService.ServerConnectionResult call() {
                NetworkConfig config = buildNetworkConfigFromForm();
                return networkConfigService.testConnectionAndLog(config, currentUser);
            }
        };

        task.setOnSucceeded(e -> {
            NetworkConfigService.ServerConnectionResult result = task.getValue();
            String status = String.format("Local: %s | Cloud: %s",
                    result.isLocalAvailable() ? "OK" : "FAILED",
                    result.isCloudAvailable() ? "OK" : "FAILED");
            connectionStatusLabel.setText(status);
            connectionStatusLabel.setStyle(result.isAnyAvailable() ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
        });

        task.setOnFailed(e -> {
            connectionStatusLabel.setText("Test failed: " + task.getException().getMessage());
            connectionStatusLabel.setStyle("-fx-text-fill: red;");
        });

        new Thread(task).start();
    }

    @FXML
    private void handleSaveNetwork() {
        try {
            NetworkConfig config = networkConfigService.getActiveConfig();
            updateNetworkConfigFromForm(config);
            networkConfigService.updateConfig(config, currentUser);
            showInfo("Network configuration saved successfully");
        } catch (Exception e) {
            showError("Failed to save network configuration: " + e.getMessage());
        }
    }

    @FXML
    private void handleResetNetwork() {
        loadNetworkConfig();
        showInfo("Network configuration reset to saved values");
    }

    private NetworkConfig buildNetworkConfigFromForm() {
        NetworkConfig config = new NetworkConfig();
        updateNetworkConfigFromForm(config);
        return config;
    }

    private void updateNetworkConfigFromForm(NetworkConfig config) {
        config.setServerType(serverTypeCombo.getValue());
        config.setLocalServerPath(localServerPathField.getText());
        config.setCloudServerUrl(cloudServerUrlField.getText());

        config.setProxyEnabled(proxyEnabledCheckbox.isSelected());
        config.setProxyType(proxyTypeCombo.getValue());
        config.setProxyHost(proxyHostField.getText());
        try {
            config.setProxyPort(Integer.parseInt(proxyPortField.getText()));
        } catch (NumberFormatException ignored) {}
        config.setProxyUsername(proxyUsernameField.getText());
        config.setProxyPassword(proxyPasswordField.getText());

        config.setCustomDnsEnabled(customDnsCheckbox.isSelected());
        config.setPrimaryDns(primaryDnsField.getText());
        config.setSecondaryDns(secondaryDnsField.getText());

        config.setSslEnabled(sslEnabledCheckbox.isSelected());
        config.setSslCertPath(sslCertPathField.getText());
        config.setSslVerifyHostname(sslVerifyHostnameCheckbox.isSelected());
    }

    // ========== Products Tab ==========

    @FXML
    private void handleRefreshProducts() {
        productCardsPane.getChildren().clear();
        productLauncherService.updateProductInstallationStatus();

        List<Product> products = productLauncherService.getAllProducts();
        for (Product product : products) {
            productCardsPane.getChildren().add(createProductCard(product));
        }

        long installed = products.stream().filter(Product::getIsInstalled).count();
        productsStatusLabel.setText(installed + " of " + products.size() + " products installed");
    }

    @FXML
    private void handleCheckUpdates() {
        showInfo("Checking for updates...");
        appUpdateService.checkAllForUpdates().thenAccept(results -> {
            long updatesFound = results.stream().filter(AppUpdateService.UpdateCheckResult::updateFound).count();
            Platform.runLater(() -> {
                if (updatesFound > 0) {
                    showInfo(updatesFound + " update(s) available. Check the Software Catalog for details.");
                } else {
                    showInfo("All products are up to date.");
                }
                handleRefreshProducts();
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> showError("Update check failed: " + ex.getMessage()));
            return null;
        });
    }

    private VBox createProductCard(Product product) {
        VBox card = new VBox(10);
        card.getStyleClass().add("product-card");
        card.setPadding(new Insets(15));
        card.setPrefWidth(220);
        card.setAlignment(Pos.TOP_CENTER);
        card.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-radius: 5;");

        Label nameLabel = new Label(product.getProductName());
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label categoryLabel = new Label(product.getCategory() != null ? product.getCategory() : "");
        categoryLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        Label versionLabel = new Label("v" + (product.getCurrentVersion() != null ? product.getCurrentVersion() : "1.0.0"));
        versionLabel.setStyle("-fx-font-size: 12px;");

        Label statusLabel = new Label(product.getIsInstalled() ? "Installed" : "Not Installed");
        statusLabel.setStyle(product.getIsInstalled() ? "-fx-text-fill: green;" : "-fx-text-fill: #999;");

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(180);
        progressBar.setVisible(false);

        Button actionButton = new Button(product.getIsInstalled() ? "Launch" : "Install");
        actionButton.getStyleClass().add("primary-button");
        actionButton.setOnAction(e -> {
            if (product.getIsInstalled()) {
                handleLaunchProduct(product);
            } else {
                handleInstallProduct(product, progressBar, actionButton);
            }
        });

        card.getChildren().addAll(nameLabel, categoryLabel, versionLabel, statusLabel, progressBar, actionButton);

        if (!product.getIsInstalled()) {
            card.setOpacity(0.7);
        }

        return card;
    }

    private void handleLaunchProduct(Product product) {
        try {
            productLauncherService.launchProduct(product.getProductCode());
            auditLogService.logProductLaunch(currentUser, product);
            showInfo("Launched " + product.getProductName());
        } catch (Exception e) {
            showError("Failed to launch product: " + e.getMessage());
        }
    }

    private void handleInstallProduct(Product product, ProgressBar progressBar, Button button) {
        button.setDisable(true);
        progressBar.setVisible(true);

        Task<DeploymentService.InstallationResult> task = deploymentService.installProduct(
                product,
                currentUser,
                progress -> Platform.runLater(() -> progressBar.setProgress(progress))
        );

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            progressBar.setVisible(false);
            button.setDisable(false);
            handleRefreshProducts();
            showInfo("Installation complete: " + product.getProductName());
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            progressBar.setVisible(false);
            button.setDisable(false);
            showError("Installation failed: " + task.getException().getMessage());
        }));

        new Thread(task).start();
    }

    // ========== Software Catalog Tab ==========

    private void setupSoftwareCatalog() {
        // Setup category filter
        softwareCategoryFilter.getItems().add(null); // "All Categories"
        softwareCategoryFilter.getItems().addAll(ThirdPartyAppCategory.values());
        softwareCategoryFilter.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ThirdPartyAppCategory item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null ? "All Categories" : item.getDisplayName());
            }
        });
        softwareCategoryFilter.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ThirdPartyAppCategory item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : (item == null ? "All Categories" : item.getDisplayName()));
            }
        });

        // Setup table columns
        swNameColumn.setCellValueFactory(new PropertyValueFactory<>("appName"));
        swPublisherColumn.setCellValueFactory(new PropertyValueFactory<>("publisher"));
        swCategoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        swCategoryColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ThirdPartyAppCategory item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getDisplayName());
            }
        });
        swVersionColumn.setCellValueFactory(new PropertyValueFactory<>("latestVersion"));
        swInstallerTypeColumn.setCellValueFactory(new PropertyValueFactory<>("installerType"));
        swInstallerTypeColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(InstallerType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.name());
            }
        });
        swApprovedColumn.setCellValueFactory(new PropertyValueFactory<>("isApproved"));
        swApprovedColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(item ? "Yes" : "No");
                    setStyle(item ? "-fx-text-fill: green;" : "-fx-text-fill: #999;");
                }
            }
        });
        swInstalledColumn.setCellValueFactory(new PropertyValueFactory<>("isInstalled"));
        swInstalledColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(item ? "Yes" : "No");
                    setStyle(item ? "-fx-text-fill: green;" : "-fx-text-fill: #999;");
                }
            }
        });

        // Setup actions column
        swActionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button installBtn = new Button("Install");
            private final Button approveBtn = new Button("Approve");
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final ProgressBar progressBar = new ProgressBar(0);
            private final HBox buttons = new HBox(5);

            {
                installBtn.getStyleClass().add("primary-button");
                approveBtn.getStyleClass().add("success-button");
                deleteBtn.getStyleClass().add("danger-button");
                progressBar.setPrefWidth(80);
                progressBar.setVisible(false);

                installBtn.setOnAction(e -> {
                    ThirdPartyApp app = getTableView().getItems().get(getIndex());
                    handleInstallThirdPartyApp(app, progressBar, installBtn);
                });

                approveBtn.setOnAction(e -> {
                    ThirdPartyApp app = getTableView().getItems().get(getIndex());
                    handleApproveApp(app);
                });

                editBtn.setOnAction(e -> {
                    ThirdPartyApp app = getTableView().getItems().get(getIndex());
                    showSoftwareDialog(app);
                });

                deleteBtn.setOnAction(e -> {
                    ThirdPartyApp app = getTableView().getItems().get(getIndex());
                    handleDeleteApp(app);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ThirdPartyApp app = getTableView().getItems().get(getIndex());
                    buttons.getChildren().clear();

                    boolean canManage = permissionService.hasPermission(currentUser, PermissionType.CAN_MANAGE_SOFTWARE_CATALOG);
                    boolean canApprove = permissionService.hasPermission(currentUser, PermissionType.CAN_APPROVE_SOFTWARE);
                    boolean canInstall = permissionService.hasPermission(currentUser, PermissionType.CAN_INSTALL_THIRD_PARTY);

                    if (app.getIsApproved() && !app.getIsInstalled() && canInstall) {
                        buttons.getChildren().addAll(installBtn, progressBar);
                    }
                    if (!app.getIsApproved() && canApprove) {
                        buttons.getChildren().add(approveBtn);
                    }
                    if (canManage) {
                        buttons.getChildren().add(editBtn);
                        if (!app.getIsInstalled()) {
                            buttons.getChildren().add(deleteBtn);
                        }
                    }

                    setGraphic(buttons);
                }
            }
        });

        // Add listeners for filtering
        softwareSearchField.textProperty().addListener((obs, old, val) -> filterSoftwareTable());
        softwareCategoryFilter.valueProperty().addListener((obs, old, val) -> filterSoftwareTable());
        showApprovedOnlyCheckbox.selectedProperty().addListener((obs, old, val) -> filterSoftwareTable());
    }

    @FXML
    private void handleRefreshSoftware() {
        List<ThirdPartyApp> apps;

        if (showApprovedOnlyCheckbox.isSelected()) {
            apps = thirdPartyAppService.getApprovedApps();
        } else {
            apps = thirdPartyAppService.getAllApps();
        }

        softwareTable.setItems(FXCollections.observableArrayList(apps));
        updateSoftwareStats();
        loadPendingApprovals();
    }

    private void filterSoftwareTable() {
        String searchTerm = softwareSearchField.getText();
        ThirdPartyAppCategory category = softwareCategoryFilter.getValue();
        boolean approvedOnly = showApprovedOnlyCheckbox.isSelected();

        List<ThirdPartyApp> apps;

        if (searchTerm != null && !searchTerm.isEmpty()) {
            apps = thirdPartyAppService.searchApps(searchTerm, approvedOnly);
        } else if (category != null) {
            apps = approvedOnly ?
                    thirdPartyAppService.getApprovedAppsByCategory(category) :
                    thirdPartyAppService.getAppsByCategory(category);
        } else {
            apps = approvedOnly ?
                    thirdPartyAppService.getApprovedApps() :
                    thirdPartyAppService.getAllApps();
        }

        softwareTable.setItems(FXCollections.observableArrayList(apps));
    }

    private void updateSoftwareStats() {
        ThirdPartyAppService.CatalogStats stats = thirdPartyAppService.getCatalogStats();
        totalSoftwareLabel.setText(String.valueOf(stats.total()));
        approvedSoftwareLabel.setText(String.valueOf(stats.approved()));
        installedSoftwareLabel.setText(String.valueOf(stats.installed()));
        pendingSoftwareLabel.setText(String.valueOf(stats.total() - stats.approved()));

        // Update count
        List<ThirdPartyApp> appsWithUpdates = appUpdateService.getAppsWithPendingUpdates();
        updatesAvailableLabel.setText(String.valueOf(appsWithUpdates.size()));
        loadPendingUpdates();
    }

    private void loadPendingUpdates() {
        pendingUpdatesFlowPane.getChildren().clear();

        List<ThirdPartyApp> pendingUpdates = appUpdateService.getAppsWithPendingUpdates();
        List<ThirdPartyApp> approvedUpdates = appUpdateService.getAppsWithApprovedUpdates();

        int totalUpdates = pendingUpdates.size() + approvedUpdates.size();

        if (totalUpdates == 0) {
            pendingUpdatesPane.setExpanded(false);
            pendingUpdatesPane.setText("Pending Updates (0)");
        } else {
            pendingUpdatesPane.setText("Pending Updates (" + totalUpdates + ")");

            // Show pending updates needing approval
            for (ThirdPartyApp app : pendingUpdates) {
                pendingUpdatesFlowPane.getChildren().add(createPendingUpdateCard(app, false));
            }

            // Show approved updates ready to install
            for (ThirdPartyApp app : approvedUpdates) {
                pendingUpdatesFlowPane.getChildren().add(createPendingUpdateCard(app, true));
            }
        }
    }

    private VBox createPendingUpdateCard(ThirdPartyApp app, boolean approved) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setPrefWidth(280);
        card.setStyle(approved ?
                "-fx-background-color: #E8F5E9; -fx-border-color: #4CAF50; -fx-border-radius: 5; -fx-background-radius: 5;" :
                "-fx-background-color: #FFF8E1; -fx-border-color: #FFC107; -fx-border-radius: 5; -fx-background-radius: 5;");

        // App name and update info
        Label nameLabel = new Label(app.getAppName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label versionLabel = new Label(app.getCurrentVersion() + " -> " + app.getPendingVersion());
        versionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        Label policyLabel = new Label("Policy: " + app.getUpdatePolicy().getDisplayName());
        policyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        // Action buttons
        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER_LEFT);

        if (!approved && permissionService.hasPermission(currentUser, PermissionType.CAN_APPROVE_SOFTWARE)) {
            Button approveBtn = new Button("Approve");
            approveBtn.getStyleClass().add("success-button");
            approveBtn.setOnAction(e -> handleApproveUpdate(app));

            Button rejectBtn = new Button("Skip");
            rejectBtn.setOnAction(e -> handleRejectUpdate(app));

            buttons.getChildren().addAll(approveBtn, rejectBtn);
        }

        if (approved && permissionService.hasPermission(currentUser, PermissionType.CAN_INSTALL_THIRD_PARTY)) {
            Button installBtn = new Button("Install Update");
            installBtn.getStyleClass().add("primary-button");
            installBtn.setOnAction(e -> handleInstallUpdate(app));

            ProgressBar progressBar = new ProgressBar(0);
            progressBar.setPrefWidth(100);
            progressBar.setVisible(false);

            buttons.getChildren().addAll(installBtn, progressBar);
        }

        // Settings button for IT admins
        if (permissionService.hasPermission(currentUser, PermissionType.CAN_MANAGE_SOFTWARE_CATALOG)) {
            Button settingsBtn = new Button("Settings");
            settingsBtn.setOnAction(e -> showUpdatePolicyDialog(app));
            buttons.getChildren().add(settingsBtn);
        }

        card.getChildren().addAll(nameLabel, versionLabel, policyLabel, buttons);

        if (app.getUpdateNotes() != null && !app.getUpdateNotes().isEmpty()) {
            Label notesLabel = new Label(app.getUpdateNotes());
            notesLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
            notesLabel.setWrapText(true);
            card.getChildren().add(notesLabel);
        }

        return card;
    }

    @FXML
    private void handleCheckAllUpdates() {
        checkAllUpdatesBtn.setDisable(true);
        checkAllUpdatesBtn.setText("Checking...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                appUpdateService.checkAllForUpdates().join();
                return null;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            checkAllUpdatesBtn.setDisable(false);
            checkAllUpdatesBtn.setText("Check All Updates");
            handleRefreshSoftware();
            showInfo("Update check complete");
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            checkAllUpdatesBtn.setDisable(false);
            checkAllUpdatesBtn.setText("Check All Updates");
            showError("Update check failed: " + task.getException().getMessage());
        }));

        new Thread(task).start();
    }

    private void handleApproveUpdate(ThirdPartyApp app) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Approve Update");
        confirm.setHeaderText("Approve update for " + app.getAppName() + "?");
        confirm.setContentText("Version: " + app.getCurrentVersion() + " -> " + app.getPendingVersion() +
                "\n\nThis will allow the update to be installed.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    appUpdateService.approveUpdate(app.getId(), currentUser);
                    handleRefreshSoftware();
                    showInfo("Update approved");
                } catch (Exception e) {
                    showError("Failed to approve update: " + e.getMessage());
                }
            }
        });
    }

    private void handleRejectUpdate(ThirdPartyApp app) {
        try {
            appUpdateService.rejectUpdate(app.getId(), currentUser);
            handleRefreshSoftware();
            showInfo("Update skipped");
        } catch (Exception e) {
            showError("Failed to skip update: " + e.getMessage());
        }
    }

    private void handleInstallUpdate(ThirdPartyApp app) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Install Update");
        confirm.setHeaderText("Install update for " + app.getAppName() + "?");
        confirm.setContentText("This will update from version " + app.getCurrentVersion() +
                " to " + app.getPendingVersion());

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Task<AppUpdateService.UpdateResult> task = appUpdateService.performUpdate(
                        app,
                        progress -> {} // We'll handle progress differently
                );

                task.setOnSucceeded(e -> Platform.runLater(() -> {
                    AppUpdateService.UpdateResult result = task.getValue();
                    if (result.success()) {
                        handleRefreshSoftware();
                        showInfo("Update installed: " + app.getAppName() + " is now at version " + result.newVersion());
                    } else {
                        showError("Update failed: " + result.errorMessage());
                    }
                }));

                task.setOnFailed(e -> Platform.runLater(() ->
                        showError("Update failed: " + task.getException().getMessage())));

                new Thread(task).start();
            }
        });
    }

    private void showUpdatePolicyDialog(ThirdPartyApp app) {
        Dialog<ThirdPartyApp> dialog = new Dialog<>();
        dialog.setTitle("Update Settings: " + app.getAppName());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        ComboBox<UpdatePolicy> policyCombo = new ComboBox<>(
                FXCollections.observableArrayList(UpdatePolicy.values()));
        policyCombo.setValue(app.getUpdatePolicy());

        Spinner<Integer> intervalSpinner = new Spinner<>(1, 168, app.getUpdateCheckIntervalHours()); // 1 hour to 1 week
        intervalSpinner.setEditable(true);

        TextField updateUrlField = new TextField(app.getUpdateCheckUrl() != null ? app.getUpdateCheckUrl() : "");
        updateUrlField.setPromptText("URL that returns version info (optional)");
        updateUrlField.setPrefWidth(350);

        grid.add(new Label("Update Policy:"), 0, 0);
        grid.add(policyCombo, 1, 0);

        grid.add(new Label("Check Interval (hours):"), 0, 1);
        grid.add(intervalSpinner, 1, 1);

        grid.add(new Label("Update Check URL:"), 0, 2);
        grid.add(updateUrlField, 1, 2);

        // Policy description
        Label descLabel = new Label(policyCombo.getValue().getDescription());
        descLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        grid.add(descLabel, 1, 3);

        policyCombo.setOnAction(e -> descLabel.setText(policyCombo.getValue().getDescription()));

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                try {
                    appUpdateService.setUpdatePolicy(app.getId(), policyCombo.getValue(), currentUser);
                    appUpdateService.setUpdateCheckInterval(app.getId(), intervalSpinner.getValue(), currentUser);

                    app.setUpdateCheckUrl(updateUrlField.getText().trim().isEmpty() ? null : updateUrlField.getText().trim());
                    thirdPartyAppService.updateApp(app, currentUser);

                    return app;
                } catch (Exception e) {
                    showError("Failed to update settings: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            handleRefreshSoftware();
            showInfo("Update settings saved");
        });
    }

    private void loadPendingApprovals() {
        pendingAppsPane.getChildren().clear();
        List<ThirdPartyApp> pendingApps = thirdPartyAppService.getPendingApps();

        if (pendingApps.isEmpty()) {
            pendingApprovalPane.setExpanded(false);
            pendingApprovalPane.setText("Pending Approval (0)");
        } else {
            pendingApprovalPane.setText("Pending Approval (" + pendingApps.size() + ")");
            for (ThirdPartyApp app : pendingApps) {
                pendingAppsPane.getChildren().add(createPendingAppCard(app));
            }
        }
    }

    private HBox createPendingAppCard(ThirdPartyApp app) {
        HBox card = new HBox(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: #fff3cd; -fx-border-color: #ffc107; -fx-border-radius: 5; -fx-background-radius: 5;");

        VBox info = new VBox(3);
        Label nameLabel = new Label(app.getAppName());
        nameLabel.setStyle("-fx-font-weight: bold;");
        Label publisherLabel = new Label(app.getPublisher() != null ? app.getPublisher() : "Unknown Publisher");
        publisherLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        info.getChildren().addAll(nameLabel, publisherLabel);

        Button approveBtn = new Button("Approve");
        approveBtn.getStyleClass().add("success-button");
        approveBtn.setOnAction(e -> handleApproveApp(app));

        Button rejectBtn = new Button("Reject");
        rejectBtn.getStyleClass().add("danger-button");
        rejectBtn.setOnAction(e -> handleDeleteApp(app));

        card.getChildren().addAll(info, new Region(), approveBtn, rejectBtn);
        HBox.setHgrow(card.getChildren().get(1), Priority.ALWAYS);

        return card;
    }

    @FXML
    private void handleAddSoftware() {
        showSoftwareDialog(null);
    }

    private void showSoftwareDialog(ThirdPartyApp app) {
        Dialog<ThirdPartyApp> dialog = new Dialog<>();
        dialog.setTitle(app == null ? "Add Software" : "Edit Software");
        dialog.setResizable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField appCodeField = new TextField(app != null ? app.getAppCode() : "");
        appCodeField.setPromptText("e.g., CHROME, FIREFOX");
        appCodeField.setDisable(app != null);

        TextField appNameField = new TextField(app != null ? app.getAppName() : "");
        appNameField.setPromptText("e.g., Google Chrome");

        TextField publisherField = new TextField(app != null ? app.getPublisher() : "");
        publisherField.setPromptText("e.g., Google LLC");

        ComboBox<ThirdPartyAppCategory> categoryCombo = new ComboBox<>(
                FXCollections.observableArrayList(ThirdPartyAppCategory.values()));
        if (app != null) categoryCombo.setValue(app.getCategory());

        ComboBox<InstallerType> installerTypeCombo = new ComboBox<>(
                FXCollections.observableArrayList(InstallerType.values()));
        if (app != null) installerTypeCombo.setValue(app.getInstallerType());

        TextField versionField = new TextField(app != null ? app.getLatestVersion() : "");
        versionField.setPromptText("e.g., 120.0.0");

        TextField downloadUrlField = new TextField(app != null ? app.getDownloadUrl() : "");
        downloadUrlField.setPromptText("https://...");
        downloadUrlField.setPrefWidth(400);

        TextField localPathField = new TextField(app != null ? app.getLocalPath() : "");
        localPathField.setPromptText("\\\\SERVER\\software\\app.exe");

        TextField silentArgsField = new TextField(app != null ? app.getSilentInstallArgs() : "");
        silentArgsField.setPromptText("/S /silent");

        TextField checksumField = new TextField(app != null ? app.getChecksumSha256() : "");
        checksumField.setPromptText("SHA-256 hash (optional)");

        TextArea descriptionArea = new TextArea(app != null ? app.getDescription() : "");
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setPromptText("Description of the software");

        CheckBox requiresAdminCheck = new CheckBox("Requires Admin");
        requiresAdminCheck.setSelected(app == null || app.getRequiresAdmin());

        CheckBox requiresRestartCheck = new CheckBox("Requires Restart");
        requiresRestartCheck.setSelected(app != null && app.getRequiresRestart());

        int row = 0;
        grid.add(new Label("App Code:*"), 0, row);
        grid.add(appCodeField, 1, row++);
        grid.add(new Label("App Name:*"), 0, row);
        grid.add(appNameField, 1, row++);
        grid.add(new Label("Publisher:"), 0, row);
        grid.add(publisherField, 1, row++);
        grid.add(new Label("Category:*"), 0, row);
        grid.add(categoryCombo, 1, row++);
        grid.add(new Label("Installer Type:*"), 0, row);
        grid.add(installerTypeCombo, 1, row++);
        grid.add(new Label("Version:"), 0, row);
        grid.add(versionField, 1, row++);
        grid.add(new Label("Download URL:"), 0, row);
        grid.add(downloadUrlField, 1, row++);
        grid.add(new Label("Local Path:"), 0, row);
        grid.add(localPathField, 1, row++);
        grid.add(new Label("Silent Args:"), 0, row);
        grid.add(silentArgsField, 1, row++);
        grid.add(new Label("Checksum:"), 0, row);
        grid.add(checksumField, 1, row++);
        grid.add(new Label("Description:"), 0, row);
        grid.add(descriptionArea, 1, row++);
        HBox checksRow = new HBox(20, requiresAdminCheck, requiresRestartCheck);
        grid.add(checksRow, 1, row);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                try {
                    ThirdPartyApp result = app != null ? app : new ThirdPartyApp();

                    if (app == null) {
                        result.setAppCode(appCodeField.getText().toUpperCase().trim());
                    }
                    result.setAppName(appNameField.getText().trim());
                    result.setPublisher(publisherField.getText().trim());
                    result.setCategory(categoryCombo.getValue());
                    result.setInstallerType(installerTypeCombo.getValue());
                    result.setLatestVersion(versionField.getText().trim());
                    result.setDownloadUrl(downloadUrlField.getText().trim());
                    result.setLocalPath(localPathField.getText().trim());
                    result.setSilentInstallArgs(silentArgsField.getText().trim());
                    result.setChecksumSha256(checksumField.getText().trim());
                    result.setDescription(descriptionArea.getText().trim());
                    result.setRequiresAdmin(requiresAdminCheck.isSelected());
                    result.setRequiresRestart(requiresRestartCheck.isSelected());

                    // Validation
                    if (result.getAppCode().isEmpty() || result.getAppName().isEmpty() ||
                            result.getCategory() == null || result.getInstallerType() == null) {
                        showError("Please fill in all required fields (marked with *)");
                        return null;
                    }

                    if (app == null) {
                        return thirdPartyAppService.addApp(result, currentUser);
                    } else {
                        return thirdPartyAppService.updateApp(result, currentUser);
                    }
                } catch (Exception e) {
                    showError("Failed to save software: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            handleRefreshSoftware();
            showInfo("Software saved successfully");
        });
    }

    private void handleApproveApp(ThirdPartyApp app) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Approve Software");
        confirm.setHeaderText("Approve " + app.getAppName() + " for deployment?");
        confirm.setContentText("This will allow users with installation permissions to install this software on their computers.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    thirdPartyAppService.approveApp(app.getId(), currentUser);
                    handleRefreshSoftware();
                    showInfo("Software approved for deployment");
                } catch (Exception e) {
                    showError("Failed to approve software: " + e.getMessage());
                }
            }
        });
    }

    private void handleDeleteApp(ThirdPartyApp app) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Software");
        confirm.setHeaderText("Remove " + app.getAppName() + " from catalog?");
        confirm.setContentText("This will remove the software from the catalog. It cannot be undone.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    thirdPartyAppService.removeApp(app.getId(), currentUser);
                    handleRefreshSoftware();
                    showInfo("Software removed from catalog");
                } catch (Exception e) {
                    showError("Failed to remove software: " + e.getMessage());
                }
            }
        });
    }

    private void handleInstallThirdPartyApp(ThirdPartyApp app, ProgressBar progressBar, Button installBtn) {
        installBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);

        Task<ThirdPartyInstallerService.InstallationResult> task = thirdPartyInstallerService.installApp(
                app,
                currentUser,
                progress -> Platform.runLater(() -> progressBar.setProgress(progress))
        );

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            progressBar.setVisible(false);
            installBtn.setDisable(false);
            ThirdPartyInstallerService.InstallationResult result = task.getValue();

            if (result.isSuccess()) {
                handleRefreshSoftware();
                showInfo("Installation complete: " + app.getAppName());
            } else {
                showError("Installation failed: " + result.getErrorMessage());
            }
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            progressBar.setVisible(false);
            installBtn.setDisable(false);
            showError("Installation failed: " + task.getException().getMessage());
        }));

        new Thread(task).start();
    }

    // ========== Devices Tab ==========

    private void setupDevicesTable() {
        deviceNameColumn.setCellValueFactory(new PropertyValueFactory<>("deviceName"));
        deviceIdColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getDeviceId() != null && data.getValue().getDeviceId().length() > 12
                        ? data.getValue().getDeviceId().substring(0, 12) + "..." : data.getValue().getDeviceId()));
        deviceMacColumn.setCellValueFactory(new PropertyValueFactory<>("macAddress"));
        deviceOsColumn.setCellValueFactory(new PropertyValueFactory<>("operatingSystem"));
        deviceAccountColumn.setCellValueFactory(new PropertyValueFactory<>("accountToken"));
        deviceStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        deviceRegisteredAtColumn.setCellValueFactory(new PropertyValueFactory<>("registeredAt"));

        // Actions column with Approve/Reject buttons
        deviceActionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button approveBtn = new Button("Approve");
            private final Button rejectBtn = new Button("Reject");
            private final HBox buttons = new HBox(5, approveBtn, rejectBtn);

            {
                approveBtn.getStyleClass().add("success-button");
                rejectBtn.getStyleClass().add("danger-button");

                approveBtn.setOnAction(e -> {
                    SisApiClient.DeviceSummary device = getTableView().getItems().get(getIndex());
                    handleApproveDevice(device);
                });

                rejectBtn.setOnAction(e -> {
                    SisApiClient.DeviceSummary device = getTableView().getItems().get(getIndex());
                    handleRejectDevice(device);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    SisApiClient.DeviceSummary device = getTableView().getItems().get(getIndex());
                    if ("PENDING_APPROVAL".equalsIgnoreCase(device.getStatus())) {
                        setGraphic(buttons);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });
    }

    @FXML
    private void handleRefreshDevices() {
        if (!permissionService.canManageDevices(currentUser)) return;

        String serverUrl = authenticationService.getCurrentSisServerUrl();
        if (serverUrl == null) {
            log.warn("No SIS server URL available for device management");
            return;
        }

        Task<List<SisApiClient.DeviceSummary>> task = new Task<>() {
            @Override
            protected List<SisApiClient.DeviceSummary> call() {
                // We need the access token - read from saved token file
                String token = null;
                try {
                    java.nio.file.Path tokenPath = java.nio.file.Paths.get(
                            System.getProperty("user.home"), ".heronix", "auth", "token.jwt");
                    if (java.nio.file.Files.exists(tokenPath)) {
                        token = java.nio.file.Files.readString(tokenPath).trim();
                    }
                } catch (Exception e) {
                    log.warn("Could not read token for device API: {}", e.getMessage());
                }
                if (token == null) return List.of();
                return deviceApprovalService.getPendingDevices(serverUrl, token);
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() ->
                devicesTable.setItems(FXCollections.observableArrayList(task.getValue()))));

        task.setOnFailed(e -> Platform.runLater(() -> {
            log.error("Failed to load pending devices", task.getException());
            showError("Failed to load devices: " + task.getException().getMessage());
        }));

        new Thread(task).start();
    }

    private void handleApproveDevice(SisApiClient.DeviceSummary device) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Approve Device");
        confirm.setHeaderText("Approve device: " + device.getDeviceName() + "?");
        confirm.setContentText("Device ID: " + device.getDeviceId().substring(0, 12) + "...\n" +
                "MAC: " + device.getMacAddress() + "\n" +
                "OS: " + device.getOperatingSystem() + "\n" +
                "Registered by: " + device.getAccountToken());

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                String serverUrl = authenticationService.getCurrentSisServerUrl();
                try {
                    java.nio.file.Path tokenPath = java.nio.file.Paths.get(
                            System.getProperty("user.home"), ".heronix", "auth", "token.jwt");
                    String token = java.nio.file.Files.readString(tokenPath).trim();

                    boolean success = deviceApprovalService.approveDevice(
                            serverUrl, token, device.getDeviceId(), currentUser.getUsername());
                    if (success) {
                        handleRefreshDevices();
                        showInfo("Device approved: " + device.getDeviceName());
                    } else {
                        showError("Failed to approve device");
                    }
                } catch (Exception e) {
                    showError("Failed to approve device: " + e.getMessage());
                }
            }
        });
    }

    private void handleRejectDevice(SisApiClient.DeviceSummary device) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reject Device");
        dialog.setHeaderText("Reject device: " + device.getDeviceName() + "?");
        dialog.setContentText("Reason (optional):");

        dialog.showAndWait().ifPresent(reason -> {
            String serverUrl = authenticationService.getCurrentSisServerUrl();
            try {
                java.nio.file.Path tokenPath = java.nio.file.Paths.get(
                        System.getProperty("user.home"), ".heronix", "auth", "token.jwt");
                String token = java.nio.file.Files.readString(tokenPath).trim();

                boolean success = deviceApprovalService.rejectDevice(
                        serverUrl, token, device.getDeviceId(), currentUser.getUsername(), reason);
                if (success) {
                    handleRefreshDevices();
                    showInfo("Device rejected: " + device.getDeviceName());
                } else {
                    showError("Failed to reject device");
                }
            } catch (Exception e) {
                showError("Failed to reject device: " + e.getMessage());
            }
        });
    }

    // ========== Logs Tab ==========

    private void setupLogsTable() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        logTimestampColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getTimestamp().format(formatter)));
        logUsernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        logActionColumn.setCellValueFactory(new PropertyValueFactory<>("action"));
        logEntityColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getEntityType() != null ?
                        data.getValue().getEntityType() + " #" + data.getValue().getEntityId() : ""));
        logSuccessColumn.setCellValueFactory(new PropertyValueFactory<>("success"));
        logDetailsColumn.setCellValueFactory(new PropertyValueFactory<>("details"));
    }

    private void setupActionFilter() {
        actionFilterCombo.setItems(FXCollections.observableArrayList(AuditAction.values()));
    }

    @FXML
    private void handleSearchLogs() {
        Page<AuditLog> logs;

        if (logSearchField.getText() != null && !logSearchField.getText().isEmpty()) {
            logs = auditLogService.searchByUsername(logSearchField.getText(), PageRequest.of(currentPage, PAGE_SIZE));
        } else if (actionFilterCombo.getValue() != null) {
            logs = auditLogService.searchByAction(actionFilterCombo.getValue(), PageRequest.of(currentPage, PAGE_SIZE));
        } else {
            logs = auditLogService.getLogs(PageRequest.of(currentPage, PAGE_SIZE));
        }

        logsTable.setItems(FXCollections.observableArrayList(logs.getContent()));
        pageInfoLabel.setText("Page " + (currentPage + 1) + " of " + logs.getTotalPages());
    }

    @FXML
    private void handlePrevPage() {
        if (currentPage > 0) {
            currentPage--;
            handleSearchLogs();
        }
    }

    @FXML
    private void handleNextPage() {
        currentPage++;
        handleSearchLogs();
    }

    @FXML
    private void handleExportLogs() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Audit Logs");
        fileChooser.setInitialFileName("audit_logs_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fileChooser.showSaveDialog(logsTable.getScene().getWindow());
        if (file != null) {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
                writer.println("ID,Timestamp,Username,Action,Details,Severity,Success,IP Address");
                // Export all logs (not just current page)
                Page<AuditLog> allLogs = auditLogService.getLogs(PageRequest.of(0, 10000));
                for (AuditLog entry : allLogs.getContent()) {
                    writer.printf("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%s,\"%s\"%n",
                            entry.getId(),
                            entry.getTimestamp() != null ? entry.getTimestamp().toString() : "",
                            entry.getUsername() != null ? entry.getUsername().replace("\"", "\"\"") : "",
                            entry.getAction() != null ? entry.getAction().name() : "",
                            entry.getDetails() != null ? entry.getDetails().replace("\"", "\"\"") : "",
                            entry.getSeverity() != null ? entry.getSeverity() : "",
                            entry.getSuccess(),
                            entry.getIpAddress() != null ? entry.getIpAddress() : "");
                }
                showInfo("Exported " + allLogs.getTotalElements() + " log entries to " + file.getName());
            } catch (Exception e) {
                log.error("Failed to export logs", e);
                showError("Export failed: " + e.getMessage());
            }
        }
    }

    // ========== Status Tab ==========

    @FXML
    private void handleRefreshStatus() {
        Task<SystemStatusService.SystemStatus> task = new Task<>() {
            @Override
            protected SystemStatusService.SystemStatus call() {
                return systemStatusService.getSystemStatus();
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            SystemStatusService.SystemStatus status = task.getValue();

            dbStatusLabel.setText(status.isDatabaseOnline() ? "Online" : "Offline");
            dbStatusLabel.setStyle(status.isDatabaseOnline() ? "-fx-text-fill: green;" : "-fx-text-fill: red;");

            localServerStatusLabel.setText(status.isLocalServerAvailable() ? "Available" : "Not Available");
            localServerStatusLabel.setStyle(status.isLocalServerAvailable() ? "-fx-text-fill: green;" : "-fx-text-fill: #999;");

            cloudServerStatusLabel.setText(status.isCloudServerAvailable() ? "Available" : "Not Available");
            cloudServerStatusLabel.setStyle(status.isCloudServerAvailable() ? "-fx-text-fill: green;" : "-fx-text-fill: #999;");

            totalUsersLabel.setText(String.valueOf(status.getTotalUsers()));
            totalProductsLabel.setText(String.valueOf(status.getTotalProducts()));
            installedProductsLabel.setText(String.valueOf(status.getInstalledProducts()));

            hubVersionLabel.setText(status.getHubVersion());
            javaVersionLabel.setText(status.getJavaVersion());
            osLabel.setText(status.getOsName() + " " + status.getOsVersion());

            double memoryPercent = status.getMemoryUsagePercent() / 100.0;
            memoryProgressBar.setProgress(memoryPercent);
            memoryLabel.setText(status.getFormattedUsedMemory() + " / " + status.getFormattedMaxMemory());
        }));

        new Thread(task).start();
    }

    // ========== Navigation ==========

    @FXML
    private void handleBack() {
        if (onBackToDashboard != null) {
            onBackToDashboard.run();
        }
    }

    // ========== Utility Methods ==========

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
