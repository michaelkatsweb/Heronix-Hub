package com.heronixedu.hub.controller;

import com.heronixedu.hub.model.Product;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.service.AuthenticationService;
import com.heronixedu.hub.service.ProductLauncherService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class DashboardController {

    @FXML
    private Label welcomeLabel;

    @FXML
    private Button logoutButton;

    @FXML
    private FlowPane productTilesContainer;

    @FXML
    private VBox noProductsMessage;

    private final AuthenticationService authenticationService;
    private final ProductLauncherService productLauncherService;
    private Runnable onLogout;
    private User currentUser;

    public DashboardController(AuthenticationService authenticationService,
                               ProductLauncherService productLauncherService) {
        this.authenticationService = authenticationService;
        this.productLauncherService = productLauncherService;
    }

    @FXML
    public void initialize() {
        // Will be called after FXML is loaded
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        welcomeLabel.setText("Welcome, " + user.getFullName());
        loadProducts();
    }

    private void loadProducts() {
        // Update product installation status
        new Thread(() -> {
            productLauncherService.updateProductInstallationStatus();

            // Get all products
            List<Product> products = productLauncherService.getAllProducts();

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

    public void refresh() {
        loadProducts();
    }
}
