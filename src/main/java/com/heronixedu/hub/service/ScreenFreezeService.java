package com.heronixedu.hub.service;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for displaying screen freeze overlay on student computers.
 * Creates a full-screen, always-on-top overlay that blocks user interaction.
 */
@Service
@Slf4j
public class ScreenFreezeService {

    private final List<Stage> overlayStages = new ArrayList<>();
    private boolean isFrozen = false;
    private String currentMessage = "Please pay attention to the teacher.";
    private String teacherName = "";

    /**
     * Freeze the screen with a message overlay.
     */
    public void freezeScreen(String message, String teacher) {
        if (isFrozen) {
            // Update message if already frozen
            updateMessage(message, teacher);
            return;
        }

        this.currentMessage = message != null ? message : "Please pay attention to the teacher.";
        this.teacherName = teacher != null ? teacher : "";

        Platform.runLater(() -> {
            try {
                // Create overlay for each screen/monitor
                for (Screen screen : Screen.getScreens()) {
                    Stage overlayStage = createOverlayStage(screen);
                    overlayStages.add(overlayStage);
                    overlayStage.show();
                }

                isFrozen = true;
                log.info("Screen frozen with message: {}", currentMessage);

            } catch (Exception e) {
                log.error("Failed to freeze screen: {}", e.getMessage());
            }
        });
    }

    /**
     * Unfreeze the screen and remove overlay.
     */
    public void unfreezeScreen() {
        if (!isFrozen) {
            return;
        }

        Platform.runLater(() -> {
            for (Stage stage : overlayStages) {
                try {
                    stage.close();
                } catch (Exception e) {
                    log.debug("Error closing overlay stage: {}", e.getMessage());
                }
            }
            overlayStages.clear();
            isFrozen = false;
            log.info("Screen unfrozen");
        });
    }

    /**
     * Update the freeze message while frozen.
     */
    public void updateMessage(String message, String teacher) {
        this.currentMessage = message != null ? message : this.currentMessage;
        this.teacherName = teacher != null ? teacher : this.teacherName;

        if (isFrozen) {
            Platform.runLater(() -> {
                // Recreate overlays with new message
                unfreezeScreen();
                freezeScreen(currentMessage, teacherName);
            });
        }
    }

    /**
     * Check if screen is currently frozen.
     */
    public boolean isFrozen() {
        return isFrozen;
    }

    /**
     * Create an overlay stage for a specific screen.
     */
    private Stage createOverlayStage(Screen screen) {
        Stage stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);

        // Position on the screen
        stage.setX(screen.getBounds().getMinX());
        stage.setY(screen.getBounds().getMinY());
        stage.setWidth(screen.getBounds().getWidth());
        stage.setHeight(screen.getBounds().getHeight());

        // Create overlay content
        VBox content = new VBox(20);
        content.setAlignment(Pos.CENTER);
        content.setStyle("-fx-background-color: rgba(0, 0, 0, 0.9);");

        // Lock icon (using Unicode character)
        Label lockIcon = new Label("🔒");
        lockIcon.setFont(Font.font("System", FontWeight.NORMAL, 72));
        lockIcon.setTextFill(Color.WHITE);

        // Title
        Label title = new Label("Screen Locked");
        title.setFont(Font.font("System", FontWeight.BOLD, 36));
        title.setTextFill(Color.web("#FF5722"));

        // Message
        Label messageLabel = new Label(currentMessage);
        messageLabel.setFont(Font.font("System", FontWeight.NORMAL, 24));
        messageLabel.setTextFill(Color.WHITE);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(600);
        messageLabel.setAlignment(Pos.CENTER);

        // Teacher name
        Label teacherLabel = new Label("");
        if (teacherName != null && !teacherName.isEmpty()) {
            teacherLabel.setText("— " + teacherName);
            teacherLabel.setFont(Font.font("System", FontWeight.NORMAL, 18));
            teacherLabel.setTextFill(Color.web("#9E9E9E"));
        }

        // Info text
        Label infoLabel = new Label("Your teacher has temporarily locked your screen.");
        infoLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        infoLabel.setTextFill(Color.web("#757575"));

        content.getChildren().addAll(lockIcon, title, messageLabel, teacherLabel, infoLabel);

        StackPane root = new StackPane(content);
        root.setStyle("-fx-background-color: rgba(0, 0, 0, 0.95);");

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        // Block ALL keyboard and mouse events using event filters
        // (filters fire before handlers and catch events before any propagation)
        scene.addEventFilter(KeyEvent.ANY, Event::consume);
        scene.addEventFilter(MouseEvent.ANY, event -> {
            // Only allow mouse movement (for visual feedback), block clicks/drags
            if (event.getEventType() != MouseEvent.MOUSE_MOVED &&
                event.getEventType() != MouseEvent.MOUSE_ENTERED &&
                event.getEventType() != MouseEvent.MOUSE_EXITED) {
                event.consume();
            }
        });

        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.setFullScreenExitHint(""); // Hide the exit hint

        // Prevent closing
        stage.setOnCloseRequest(event -> {
            if (isFrozen) {
                event.consume();
            }
        });

        return stage;
    }

    /**
     * Show attention alert (brief flash to get student's attention).
     */
    public void showAttentionAlert() {
        Platform.runLater(() -> {
            Stage alertStage = new Stage();
            alertStage.initStyle(StageStyle.UNDECORATED);
            alertStage.setAlwaysOnTop(true);

            Screen primaryScreen = Screen.getPrimary();
            alertStage.setWidth(400);
            alertStage.setHeight(150);
            alertStage.setX(primaryScreen.getBounds().getWidth() - 420);
            alertStage.setY(20);

            VBox content = new VBox(10);
            content.setAlignment(Pos.CENTER);
            content.setStyle("-fx-background-color: #FF5722; -fx-background-radius: 10; -fx-padding: 20;");

            Label icon = new Label("⚠");
            icon.setFont(Font.font("System", FontWeight.BOLD, 32));
            icon.setTextFill(Color.WHITE);

            Label message = new Label("Your teacher requests your attention!");
            message.setFont(Font.font("System", FontWeight.BOLD, 16));
            message.setTextFill(Color.WHITE);

            content.getChildren().addAll(icon, message);

            Scene scene = new Scene(content);
            alertStage.setScene(scene);
            alertStage.show();

            // Auto-close after 5 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    Platform.runLater(alertStage::close);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
    }

    /**
     * Show private message from teacher.
     */
    public void showPrivateMessage(String message, String teacher) {
        Platform.runLater(() -> {
            Stage messageStage = new Stage();
            messageStage.initStyle(StageStyle.UNDECORATED);
            messageStage.setAlwaysOnTop(true);

            Screen primaryScreen = Screen.getPrimary();
            messageStage.setWidth(400);
            messageStage.setHeight(200);
            messageStage.setX((primaryScreen.getBounds().getWidth() - 400) / 2);
            messageStage.setY((primaryScreen.getBounds().getHeight() - 200) / 2);

            VBox content = new VBox(15);
            content.setAlignment(Pos.CENTER);
            content.setStyle("-fx-background-color: #1976D2; -fx-background-radius: 10; -fx-padding: 20;");

            Label title = new Label("Message from " + teacher);
            title.setFont(Font.font("System", FontWeight.BOLD, 16));
            title.setTextFill(Color.WHITE);

            Label messageLabel = new Label(message);
            messageLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
            messageLabel.setTextFill(Color.WHITE);
            messageLabel.setWrapText(true);
            messageLabel.setMaxWidth(360);

            Label closeHint = new Label("Click anywhere to close");
            closeHint.setFont(Font.font("System", FontWeight.NORMAL, 11));
            closeHint.setTextFill(Color.web("#90CAF9"));

            content.getChildren().addAll(title, messageLabel, closeHint);

            Scene scene = new Scene(content);
            scene.setOnMouseClicked(e -> messageStage.close());

            messageStage.setScene(scene);
            messageStage.show();

            // Auto-close after 30 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(30000);
                    Platform.runLater(() -> {
                        if (messageStage.isShowing()) {
                            messageStage.close();
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
    }
}
