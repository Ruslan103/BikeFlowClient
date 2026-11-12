package org.bike.fx.workorder;
import javafx.fxml.FXML;
import javafx.scene.control.*;
public final class CustomerPartEditorController {
    @FXML TextField name, brand, model, serialNumber, quantity;
    @FXML TextArea conditionOnReceipt, reportedIssue, includedAccessories;
    @FXML Label validation;
}
