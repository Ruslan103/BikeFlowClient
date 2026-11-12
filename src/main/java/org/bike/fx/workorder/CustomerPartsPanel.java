package org.bike.fx.workorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.math.BigDecimal;
import java.util.List;

public final class CustomerPartsPanel {
    private static final ObjectMapper JSON = new ObjectMapper();
    @FXML private VBox root;
    @FXML private TableView<JsonNode> table;
    private Runnable bicycleRemoved = () -> {};
    private Runnable changed = () -> {};
    @FXML private void initialize() {
        String[] fields = {"name", "quantity", "conditionOnReceipt"};
        for (int i=0;i<fields.length;i++) {
            String field=fields[i];
            @SuppressWarnings("unchecked") TableColumn<JsonNode,String> col=(TableColumn<JsonNode,String>)table.getColumns().get(i);
            col.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyStringWrapper(c.getValue().path(field).asText("")));
            col.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
            col.setOnEditCommit(event -> {
                if (event.getRowValue().hasNonNull("bicycleId") && !field.equals("conditionOnReceipt")) { table.refresh(); return; }
                try {
                    String text = event.getNewValue() == null ? "" : event.getNewValue().strip();
                    ObjectNode result = event.getRowValue().deepCopy();
                    if (field.equals("quantity")) {
                        BigDecimal quantity = new BigDecimal(text.replace(',', '.'));
                        if (quantity.signum() <= 0 || quantity.scale() > 3
                                || quantity.precision() - quantity.scale() > 9) throw new IllegalArgumentException();
                        result.put(field, quantity);
                    } else {
                        if (field.equals("name") && (text.isBlank() || text.length() > 150)) throw new IllegalArgumentException();
                        result.put(field, text);
                    }
                    table.getItems().set(event.getTablePosition().getRow(), result);
                    changed.run();
                } catch (IllegalArgumentException exception) {
                    table.refresh();
                    Alert alert = new Alert(Alert.AlertType.WARNING, field.equals("quantity")
                            ? "Введите количество больше нуля: до 9 целых и 3 дробных знаков."
                            : "Введите название детали длиной от 1 до 150 символов.");
                    alert.initOwner(root.getScene().getWindow());
                    alert.showAndWait();
                }
            });
        }
        MenuItem add = new MenuItem("Добавить деталь клиента"), edit = new MenuItem("Изменить"), remove = new MenuItem("Удалить");
        add.setOnAction(e -> add()); edit.setOnAction(e -> edit()); remove.setOnAction(e -> remove());
        table.setContextMenu(new ContextMenu(add, edit, remove));
        table.setRowFactory(view -> {
            TableRow<JsonNode> row = new TableRow<>();
            row.setOnContextMenuRequested(e -> { if (!row.isEmpty()) table.getSelectionModel().select(row.getIndex()); });
            return row;
        });
        table.setOnKeyPressed(e -> { if(e.getCode()==javafx.scene.input.KeyCode.DELETE && table.getEditingCell() == null) { remove(); e.consume(); } });
    }
    void onChanged(Runnable action) { changed=action; }
    List<JsonNode> items() { return table.getItems().stream().filter(n -> !n.hasNonNull("bicycleId")).toList(); }
    void items(List<JsonNode> value) {
        JsonNode bicycle = table.getItems().stream().filter(n -> n.hasNonNull("bicycleId")).findFirst().orElse(null);
        table.getItems().setAll(value);
        if (bicycle != null) table.getItems().addFirst(bicycle);
    }
    void onBicycleRemoved(Runnable action) { bicycleRemoved = action; }
    String bicycleCondition() {
        return table.getItems().stream().filter(n -> n.hasNonNull("bicycleId")).findFirst()
                .map(n -> n.path("conditionOnReceipt").asText("")).orElse(null);
    }
    void bicycleCondition(String value) {
        table.getItems().stream().filter(n -> n.hasNonNull("bicycleId")).findFirst()
                .ifPresent(n -> ((ObjectNode)n).put("conditionOnReceipt", value));
        table.refresh();
    }
    void bicycle(Long id, String name, String condition) {
        JsonNode previous = table.getItems().stream().filter(n -> n.hasNonNull("bicycleId") && n.path("bicycleId").asLong() == (id == null ? -1 : id)).findFirst().orElse(null);
        if (previous != null) condition = previous.path("conditionOnReceipt").asText("");
        table.getItems().removeIf(n -> n.hasNonNull("bicycleId"));
        if (id != null) {
            ObjectNode row = JSON.createObjectNode(); row.put("bicycleId", id);
            row.put("name", "Велосипед · " + name); row.put("quantity", 1);
            row.put("conditionOnReceipt", condition == null ? "" : condition);
            table.getItems().addFirst(row);
        }
    }
    @FXML private void add() { open(-1); }
    private void edit() { int index=table.getSelectionModel().getSelectedIndex(); if(index>=0 && !table.getItems().get(index).hasNonNull("bicycleId")) open(index); }
    private void remove() { int index=table.getSelectionModel().getSelectedIndex(); if(index>=0) { JsonNode removed = table.getItems().remove(index); if (removed.hasNonNull("bicycleId")) bicycleRemoved.run(); changed.run(); } }
    private void open(int index) {
        FXMLLoader loader=new FXMLLoader(getClass().getResource("/org/bike/fx/CustomerPartEditor.fxml"));
        try {
            DialogPane pane=loader.load(); CustomerPartEditorController editor=loader.getController();
            JsonNode source=index<0 ? JSON.createObjectNode() : table.getItems().get(index);
            TextInputControl[] controls={editor.name,editor.brand,editor.model,editor.serialNumber,editor.quantity,editor.conditionOnReceipt,editor.reportedIssue,editor.includedAccessories};
            String[] fields={"name","brand","model","serialNumber","quantity","conditionOnReceipt","reportedIssue","includedAccessories"};
            for(int i=0;i<fields.length;i++) controls[i].setText(source.path(fields[i]).asText(fields[i].equals("quantity")?"1":""));
            editor.validation.managedProperty().bind(editor.validation.textProperty().isNotEmpty());
            editor.validation.visibleProperty().bind(editor.validation.managedProperty());
            Dialog<ButtonType> dialog=new Dialog<>(); dialog.setTitle(index<0?"Добавить деталь клиента":"Изменить деталь клиента"); dialog.initOwner(root.getScene().getWindow()); dialog.setDialogPane(pane);
            ButtonType save=pane.getButtonTypes().stream().filter(b -> b.getButtonData()==ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
            pane.lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION,e -> {
                try {
                    BigDecimal quantity=new BigDecimal(editor.quantity.getText().strip().replace(',','.'));
                    if(editor.name.getText().isBlank() || editor.name.getText().strip().length()>150 || quantity.signum()<=0 || quantity.scale()>3 || quantity.precision()-quantity.scale()>9) throw new IllegalArgumentException();
                    for(TextField field : new TextField[]{editor.brand,editor.model,editor.serialNumber}) if(field.getText().strip().length()>100) throw new IllegalArgumentException();
                    ObjectNode result=JSON.createObjectNode(); for(int i=0;i<fields.length;i++) result.put(fields[i],controls[i].getText().strip()); result.put("quantity",quantity);
                    if(index<0) table.getItems().add(result); else table.getItems().set(index,result); changed.run();
                } catch(RuntimeException ex) { editor.validation.setText("Проверьте название, количество и длину полей."); dialog.getDialogPane().getScene().getWindow().sizeToScene(); e.consume(); }
            });
            dialog.showAndWait();
        } catch(java.io.IOException ex) { new Alert(Alert.AlertType.ERROR,"Не удалось открыть форму детали: "+ex.getMessage()).showAndWait(); }
    }
}

