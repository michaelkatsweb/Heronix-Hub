package com.heronixedu.hub.controller;

import com.heronixedu.hub.model.User;
import com.heronixedu.hub.service.AuthenticationService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    @FXML
    private Button loginButton;

    private final AuthenticationService authenticationService;
    private Runnable onLoginSuccess;

    public LoginController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @FXML
    public void initialize() {
        // Focus on username field when view loads
        Platform.runLater(() -> usernameField.requestFocus());
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Validate input
        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password");
            return;
        }

        // Disable button during login
        loginButton.setDisable(true);
        errorLabel.setVisible(false);

        // Perform login in background
        new Thread(() -> {
            try {
                User user = authenticationService.login(username, password);

                // Success - switch to dashboard
                Platform.runLater(() -> {
                    if (onLoginSuccess != null) {
                        onLoginSuccess.run();
                    }
                });

            } catch (RuntimeException e) {
                // Show error
                Platform.runLater(() -> {
                    showError(e.getMessage());
                    loginButton.setDisable(false);
                    passwordField.clear();
                    passwordField.requestFocus();
                });
            }
        }).start();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }

    public void clearFields() {
        usernameField.clear();
        passwordField.clear();
        errorLabel.setVisible(false);
        loginButton.setDisable(false);
    }
}
