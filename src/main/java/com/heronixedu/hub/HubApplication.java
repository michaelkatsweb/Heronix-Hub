package com.heronixedu.hub;

import com.heronixedu.hub.controller.DashboardController;
import com.heronixedu.hub.controller.LoginController;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.service.AuthenticationService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

@SpringBootApplication
@Slf4j
public class HubApplication extends Application {

    private ConfigurableApplicationContext springContext;
    private Stage primaryStage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        // Start Spring Boot context
        springContext = SpringApplication.run(HubApplication.class);
        log.info("Spring Boot context initialized");
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("Heronix Hub");
        primaryStage.setResizable(true);

        // Check for existing token
        AuthenticationService authService = springContext.getBean(AuthenticationService.class);
        User existingUser = authService.checkExistingToken();

        if (existingUser != null) {
            log.info("Valid token found, auto-login for user: {}", existingUser.getUsername());
            showDashboard(existingUser);
        } else {
            log.info("No valid token found, showing login screen");
            showLogin();
        }

        primaryStage.show();
    }

    private void showLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            LoginController controller = loader.getController();
            controller.setOnLoginSuccess(() -> {
                AuthenticationService authService = springContext.getBean(AuthenticationService.class);
                User user = authService.checkExistingToken();
                if (user != null) {
                    showDashboard(user);
                }
            });

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/hub-style.css").toExternalForm());

            primaryStage.setScene(scene);
            primaryStage.centerOnScreen();

        } catch (IOException e) {
            log.error("Error loading login screen", e);
            showErrorAndExit("Failed to load login screen: " + e.getMessage());
        }
    }

    private void showDashboard(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            DashboardController controller = loader.getController();
            controller.setCurrentUser(user);
            controller.setOnLogout(this::showLogin);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/hub-style.css").toExternalForm());

            primaryStage.setScene(scene);
            primaryStage.centerOnScreen();

        } catch (IOException e) {
            log.error("Error loading dashboard", e);
            showErrorAndExit("Failed to load dashboard: " + e.getMessage());
        }
    }

    private void showErrorAndExit(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Application Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
        Platform.exit();
    }

    @Override
    public void stop() {
        // Close Spring Boot context
        if (springContext != null) {
            springContext.close();
        }
        log.info("Application stopped");
    }
}
