package org.bike.fx.workorder;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.bike.workorder.model.ProductSupplyStatus;

public final class ProductEditorController {
    @FXML TextField name;
    @FXML TextField unit;
    @FXML TextField article;
    @FXML TextField quantity;
    @FXML TextField price;
    @FXML ComboBox<JsonNode> discount;
    @FXML ComboBox<ProductSupplyStatus> supply;
    @FXML Label validation;

    @FXML
    private void initialize() {
        validation.managedProperty().bind(validation.textProperty().isNotEmpty());
        validation.visibleProperty().bind(validation.managedProperty());
        validation.textProperty().addListener((observable, previous, current) ->
                javafx.application.Platform.runLater(() -> {
                    if (validation.getScene() != null && validation.getScene().getWindow() != null
                            && validation.getScene().getWindow().isShowing()) {
                        validation.getScene().getWindow().sizeToScene();
                    }
                }));
    }
}
