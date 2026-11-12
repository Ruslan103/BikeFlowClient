package org.bike.fx;

import javafx.animation.PauseTransition;
import javafx.fxml.FXMLLoader;
import javafx.event.Event;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.io.IOException;

public final class FxView {
    private FxView() {}

    public record Loaded<T>(Parent root, T controller) {}

    public static <T> Loaded<T> load(String name) {
        try {
            FXMLLoader loader =
                    new FXMLLoader(FxView.class.getResource("/org/bike/fx/" + name + ".fxml"));
            return new Loaded<>(loader.load(), loader.getController());
        } catch (IOException error) {
            throw new IllegalStateException("Не удалось открыть форму «" + name + "».", error);
        }
    }

    public static Stage dialog(Window owner, String title, Parent root) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title);
        stage.setScene(scene(root));
        stage.setMinWidth(650);
        stage.setMinHeight(420);
        stage.getScene()
                .addEventFilter(
                        KeyEvent.KEY_PRESSED,
                        event -> {
                            if (event.getCode() != KeyCode.ESCAPE) return;
                            event.consume();
                            var closeRequest =
                                    new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
                            Event.fireEvent(stage, closeRequest);
                            if (!closeRequest.isConsumed()) stage.close();
                        });
        return stage;
    }

    public static Alert confirmation(String message, ButtonType... buttons) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, null, buttons);
        Label content = new Label(message);
        content.setWrapText(true);
        content.setMaxWidth(420);
        var pane = alert.getDialogPane();
        pane.setContent(content);
        pane.setStyle("-fx-padding: 12;");
        pane.setMinWidth(Region.USE_PREF_SIZE);
        pane.setPrefWidth(Region.USE_COMPUTED_SIZE);
        pane.setMinHeight(Region.USE_PREF_SIZE);
        pane.setPrefHeight(Region.USE_COMPUTED_SIZE);
        return alert;
    }

    public static void toast(Window owner, String message) {
        if (owner == null || !owner.isShowing()) return;
        Label content = new Label(message);
        content.setStyle(
                "-fx-background-color: #333333; -fx-text-fill: white; "
                        + "-fx-padding: 12 18; -fx-background-radius: 6;");
        Popup popup = new Popup();
        popup.getContent().add(content);
        popup.setAutoFix(true);
        popup.setAutoHide(true);
        popup.show(owner);
        popup.setX(owner.getX() + (owner.getWidth() - popup.getWidth()) / 2);
        popup.setY(owner.getY() + owner.getHeight() - popup.getHeight() - 32);
        PauseTransition timeout = new PauseTransition(Duration.seconds(2));
        timeout.setOnFinished(event -> popup.hide());
        timeout.play();
    }

    public static Scene scene(Parent root) {
        Scene scene = new Scene(root);
        return scene;
    }

    public static void error(Window owner, Exception error) {
        Alert alert = new Alert(Alert.AlertType.ERROR, error.getMessage());
        alert.initOwner(owner);
        alert.setHeaderText("Не удалось выполнить действие");
        alert.showAndWait();
    }
}
