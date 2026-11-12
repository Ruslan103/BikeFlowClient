package org.bike.fx.client;

import javafx.fxml.FXML;
import javafx.scene.control.*;

import org.bike.client.api.dto.*;
import org.bike.fx.FxContext;
import org.bike.fx.FxView;

import java.util.function.Consumer;

public final class BicycleEditorController {
    @FXML private javafx.scene.layout.VBox root;
    @FXML private TextField brand;
    @FXML private TextArea comment;
    @FXML private Button saveButton;
    @FXML private Label status;
    private FxContext context;
    private Long clientId;
    private ClientBicycleData bicycle;
    private Consumer<ClientData> saved;
    private Consumer<ClientBicycleData> stagedSave;
    private boolean busy, dirty, staged;

    @FXML
    private void initialize() {
        org.bike.fx.StatusMessages.install(status);
        brand.textProperty().addListener((o, a, b) -> dirty = true);
        comment.textProperty().addListener((o, a, b) -> dirty = true);
        brand.setOnAction(event -> save());
    }

    public void bind(
            FxContext context,
            Long clientId,
            ClientBicycleData bicycle,
            Consumer<ClientData> saved) {
        this.context = context;
        this.clientId = clientId;
        this.bicycle = bicycle;
        this.saved = saved;
        this.staged = false;
        this.stagedSave = null;
        saveButton.setText("Сохранить велосипед");
        display();
    }

    public void bindStaged(
            FxContext context,
            Long clientId,
            ClientBicycleData bicycle,
            Consumer<ClientBicycleData> stagedSave) {
        this.context = context;
        this.clientId = clientId;
        this.bicycle = bicycle;
        this.staged = true;
        this.stagedSave = stagedSave;
        saveButton.setText(
                bicycle == null || bicycle.id() == null
                        ? "Добавить в карточку"
                        : "Сохранить в карточку");
        display();
    }

    private void display() {
        brand.setText(bicycle == null ? "" : bicycle.brandName());
        comment.setText(bicycle == null ? "" : bicycle.comment());
        dirty = false;
    }

    @FXML
    private void save() {
        if (context == null || busy) return;
        busy = true;
        root.setDisable(true);
        var request =
                new BicycleCardRequest(
                        bicycle == null ? null : bicycle.version(),
                        brand.getText(),
                        comment.getText());
        if (staged) {
            ClientBicycleData value =
                    new ClientBicycleData(
                            bicycle == null ? null : bicycle.id(),
                            bicycle == null ? null : bicycle.brandId(),
                            request.brandName(),
                            request.comment(),
                            bicycle == null ? null : bicycle.version());
            busy = false;
            root.setDisable(false);
            dirty = false;
            stagedSave.accept(value);
            return;
        }
        Consumer<ClientData> success =
                value -> {
                    busy = false;
                    root.setDisable(false);
                    dirty = false;
                    saved.accept(value);
                };
        Consumer<Exception> failure =
                error -> {
                    busy = false;
                    root.setDisable(false);
                    org.bike.fx.StatusMessages.error(status, error.getMessage());
                };
        if (bicycle == null) context.clients.createBicycle(clientId, request, success, failure);
        else context.clients.updateBicycle(clientId, bicycle.id(), request, success, failure);
    }

    public boolean canLeave() {
        if (busy) return false;
        if (!dirty) return true;
        var dialog =
                FxView.confirmation(
                        "Закрыть без сохранения изменений велосипеда?",
                        ButtonType.YES,
                        ButtonType.NO);
        dialog.initOwner(brand.getScene().getWindow());
        return dialog.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }
}
