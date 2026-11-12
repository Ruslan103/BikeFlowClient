package org.bike.fx.workorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import org.bike.fx.FxContext;
import org.bike.workorder.model.ProductSupplyStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

public final class ProductsPanel {
    @javafx.fxml.FXML private VBox root;

    private org.bike.fx.SearchableComboBox<JsonNode> productOptions;
    private final java.util.ArrayList<JsonNode> catalog = new java.util.ArrayList<>();
    private static final ObjectMapper JSON = new ObjectMapper();
    @javafx.fxml.FXML private TableView<JsonNode> table;
    @javafx.fxml.FXML private ComboBox<JsonNode> choices;
    @javafx.fxml.FXML private Label total;
    private FxContext context;
    private Consumer<Exception> errors;
    private Runnable changed;
    private final java.util.ArrayList<JsonNode> discounts = new java.util.ArrayList<>();

    public ProductsPanel() { }

    void bind(FxContext context, Consumer<Exception> errors, Runnable changed) {
        this.context = context; this.errors = errors; this.changed = changed;
        productOptions = org.bike.fx.SearchableComboBox.install(choices, n -> n.path("name").asText());
        productOptions.onCommit(this::addSelected);
        MenuItem create = new MenuItem("Создать новый товар");
        create.setOnAction(e -> dialog(null, -1, true, choices.getEditor().getText()));
        MenuItem edit = new MenuItem("Изменить"), remove = new MenuItem("Удалить");
        edit.setOnAction(e -> { int i = table.getSelectionModel().getSelectedIndex(); if (i >= 0) dialog(table.getItems().get(i), i, false, ""); });
        remove.setOnAction(e -> removeSelected());
        table.setContextMenu(new ContextMenu(create, new SeparatorMenuItem(), edit, remove));
        table.setRowFactory(view -> {
            TableRow<JsonNode> row = new TableRow<>();
            row.setOnContextMenuRequested(e -> { if (!row.isEmpty()) table.getSelectionModel().select(row.getIndex()); });
            return row;
        });
        table.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.DELETE && table.getEditingCell() == null) {
                removeSelected(); e.consume();
            }
        });
        String[] fields = {"productNameSnapshot", "quantity", "priceWithoutDiscount", "discountName", "lineTotal"};
        for (int i = 0; i < fields.length; i++) {
            String field = fields[i];
            @SuppressWarnings("unchecked")
            TableColumn<JsonNode, String> column = (TableColumn<JsonNode, String>) table.getColumns().get(i);
            column.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().path(field).asText("")));
        }
        @SuppressWarnings("unchecked")
        TableColumn<JsonNode, String> statusColumn = (TableColumn<JsonNode, String>) table.getColumns().get(5);
        statusColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(status(c.getValue()).toString()));
        configureEditing();
        updateTotal();
        context.connection.request("GET", "/api/discounts", null, response -> {
            try { JSON.readTree(response.body()).forEach(discounts::add); configureDiscountEditing(); } catch (Exception ex) { errors.accept(ex); }
        }, errors);
    }
    private void loadCatalog() {
        context.connection.request("GET", "/api/products", null, response -> {
            try {
                catalog.clear();
                JSON.readTree(response.body()).forEach(n -> { if (n.path("active").asBoolean()) catalog.add(n); });
                table.getItems().stream().filter(n -> n.hasNonNull("newProduct")).forEach(this::rememberPending);
                productOptions.setItems(catalog);
            } catch (Exception ex) { errors.accept(ex); }
        }, errors);
    }
    private void rememberPending(JsonNode row) {
        ObjectNode candidate = row.path("newProduct").deepCopy();
        candidate.set("newProduct", row.path("newProduct").deepCopy());
        candidate.set("currentPrice", row.path("priceWithoutDiscount"));
        catalog.removeIf(n -> n.hasNonNull("newProduct") && n.path("name").equals(candidate.path("name")));
        catalog.add(candidate);
    }
    private void addSelected() {
        JsonNode selected = choices.getValue();
        if (selected == null) return;
        if (!selected.hasNonNull("currentPrice")) { errors.accept(new IllegalArgumentException("Для товара нет действующей цены.")); return; }
        ObjectNode row = JSON.createObjectNode();
        if (selected.hasNonNull("newProduct")) row.set("newProduct", selected.path("newProduct").deepCopy());
        else row.set("productId", selected.path("id"));
        row.set("productNameSnapshot", selected.path("name")); row.set("unitSnapshot", selected.path("unit"));
        row.set("articleSnapshot", selected.path("article")); row.put("quantity", BigDecimal.ONE);
        row.set("priceWithoutDiscount", selected.path("currentPrice")); row.set("priceWithDiscount", selected.path("currentPrice"));
        row.set("lineTotal", selected.path("currentPrice")); row.put("supplyStatus", "IN_STOCK");
        table.getItems().add(row); updateTotal();
    }
    private void removeSelected() {
        int index = table.getSelectionModel().getSelectedIndex();
        if (index >= 0) { table.getItems().remove(index); updateTotal(); }
    }
    @SuppressWarnings("unchecked")
    private TableColumn<JsonNode, String> column(int index) {
        return (TableColumn<JsonNode, String>) table.getColumns().get(index);
    }
    private void configureEditing() {
        table.setEditable(true);
        for (int index : new int[] {1, 2, 4}) {
            TableColumn<JsonNode, String> col = column(index);
            col.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
            col.setOnEditCommit(e -> {
                try {
                    BigDecimal value = new BigDecimal(e.getNewValue().strip().replace(',', '.'));
                    if (value.signum() < 0 || (index == 1 && value.signum() == 0) || value.scale() > (index == 1 ? 3 : 2)) throw new IllegalArgumentException();
                    ObjectNode row = e.getRowValue().deepCopy();
                    if (index == 1) row.put("quantity", value);
                    else if (index == 2) row.put("priceWithoutDiscount", value);
                    else row.put("priceWithDiscount", value.divide(row.path("quantity").decimalValue(), 2, RoundingMode.HALF_UP));
                    recalculate(row, index != 4);
                    table.getItems().set(e.getTablePosition().getRow(), row); updateTotal();
                } catch (RuntimeException ex) { errors.accept(new IllegalArgumentException("Введите корректное количество или цену.")); table.refresh(); }
            });
        }
        TableColumn<JsonNode, String> state = column(5);
        state.setCellFactory(javafx.scene.control.cell.ComboBoxTableCell.forTableColumn(
                java.util.Arrays.stream(ProductSupplyStatus.values()).map(Object::toString).toArray(String[]::new)));
        state.setOnEditCommit(e -> {
            ObjectNode row = e.getRowValue().deepCopy();
            for (ProductSupplyStatus value : ProductSupplyStatus.values()) if (value.toString().equals(e.getNewValue())) row.put("supplyStatus", value.name());
            table.getItems().set(e.getTablePosition().getRow(), row); updateTotal();
        });
    }
    private void configureDiscountEditing() {
        var names = javafx.collections.FXCollections.<String>observableArrayList("Без скидки");
        discounts.forEach(d -> names.add(d.path("name").asText()));
        TableColumn<JsonNode, String> col = column(3);
        col.setCellFactory(column -> new TableCell<>() {
            private final ComboBox<String> editor = new ComboBox<>();
            private final org.bike.fx.SearchableComboBox<String> options =
                    org.bike.fx.SearchableComboBox.install(editor, value -> value);
            {
                editor.setMaxWidth(Double.MAX_VALUE);
                options.onSelection(() -> commitEdit(editor.getValue()));
                options.onCommit(() -> commitEdit(editor.getValue()));
            }
            @Override
            public void startEdit() {
                if (isEmpty()) return;
                super.startEdit();
                options.setItems(names);
                editor.setValue(getItem() == null || getItem().isBlank() ? "Без скидки" : getItem());
                setText(null);
                setGraphic(editor);
                editor.show();
            }
            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setGraphic(null);
                setText(getItem());
            }
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                } else if (isEditing()) {
                    editor.setValue(value == null || value.isBlank() ? "Без скидки" : value);
                    setText(null);
                    setGraphic(editor);
                } else {
                    setGraphic(null);
                    setText(value == null || value.isBlank() ? "Без скидки" : value);
                }
            }
        });
        col.setOnEditCommit(e -> {
            ObjectNode row = e.getRowValue().deepCopy(); row.remove(java.util.List.of("discountId", "discountName", "discountPercentage"));
            discounts.stream().filter(d -> d.path("name").asText().equals(e.getNewValue())).findFirst().ifPresent(d -> {
                row.set("discountId", d.path("id")); row.set("discountName", d.path("name")); row.set("discountPercentage", d.path("percentage"));
            });
            recalculate(row, true); table.getItems().set(e.getTablePosition().getRow(), row); updateTotal();
        });
    }
    private void recalculate(ObjectNode row, boolean applyDiscount) {
        if (applyDiscount) {
            BigDecimal percentage = discounts.stream().filter(d -> d.path("id").equals(row.path("discountId")))
                    .findFirst().map(d -> d.path("percentage").decimalValue()).orElse(BigDecimal.ZERO);
            row.put("priceWithDiscount", row.path("priceWithoutDiscount").decimalValue()
                    .multiply(BigDecimal.valueOf(100).subtract(percentage)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        row.put("lineTotal", row.path("quantity").decimalValue().multiply(row.path("priceWithDiscount").decimalValue()).setScale(2, RoundingMode.HALF_UP));
    }
    List<JsonNode> items() { return List.copyOf(table.getItems()); }
    void items(List<JsonNode> items) { table.getItems().setAll(items == null ? List.of() : items); updateTotal(); loadCatalog(); }
    BigDecimal amount() { return table.getItems().stream().map(n -> n.path("lineTotal").decimalValue()).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private BigDecimal costBeforeDiscount() {
        return table.getItems().stream()
                .map(row -> row.path("quantity").decimalValue()
                        .multiply(row.path("priceWithoutDiscount").decimalValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private void updateTotal() {
        total.setText("Всего товаров: " + table.getItems().size()
                + "     Стоимость: " + costBeforeDiscount().toPlainString()
                + "     Со скидкой: " + amount().toPlainString());
        changed.run();
    }
    private ProductSupplyStatus status(JsonNode node) {
        try { return ProductSupplyStatus.valueOf(node.path("supplyStatus").asText("IN_STOCK")); }
        catch (IllegalArgumentException e) { return ProductSupplyStatus.IN_STOCK; }
    }
    private void dialog(JsonNode source, int index, boolean fresh, String initialName) {
        javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                ProductsPanel.class.getResource("/org/bike/fx/ProductEditor.fxml"));
        DialogPane editorPane;
        try { editorPane = loader.load(); }
        catch (java.io.IOException exception) { errors.accept(exception); return; }
        ProductEditorController editor = loader.getController();
        boolean pending = source != null && source.hasNonNull("newProduct");
        JsonNode product = pending ? source.path("newProduct") : source;
        String nameValue = product == null ? initialName : product.path(index < 0 || pending ? "name" : "productNameSnapshot").asText();
        TextField name = editor.name;
        name.setText(nameValue);
        TextField unit = editor.unit;
        unit.setText(product == null ? "шт" : product.path(index < 0 || pending ? "unit" : "unitSnapshot").asText("шт"));
        TextField article = editor.article;
        article.setText(product == null ? "" : product.path(index < 0 || pending ? "article" : "articleSnapshot").asText(""));
        TextField quantity = editor.quantity;
        quantity.setText(index >= 0 ? source.path("quantity").asText("1") : "1");
        TextField price = editor.price;
        price.setText(product == null ? "" : (index < 0 ? product.path("currentPrice") : source.path("priceWithoutDiscount")).asText(""));
        boolean newProduct = fresh || pending;
        name.setEditable(newProduct); unit.setEditable(newProduct); article.setEditable(newProduct);
        ComboBox<ProductSupplyStatus> supply = editor.supply; supply.getItems().setAll(ProductSupplyStatus.values()); supply.setValue(source == null ? ProductSupplyStatus.TO_ORDER : status(source));
        ComboBox<JsonNode> discount = editor.discount; discount.getItems().add(null); discount.getItems().addAll(discounts);
        discount.setConverter(new StringConverter<>() { public String toString(JsonNode n) { return n == null ? "Без скидки" : n.path("name").asText(); } public JsonNode fromString(String s) { return null; } });
        if (index >= 0) discounts.stream().filter(d -> d.path("id").equals(source.path("discountId"))).findFirst().ifPresent(discount::setValue);
        Label validation = editor.validation;
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle(newProduct ? "Новый товар в бланке" : "Товар в бланке");
        if (root.getScene() != null) dialog.initOwner(root.getScene().getWindow()); dialog.setDialogPane(editorPane);
        dialog.getDialogPane().lookupButton(editorPane.getButtonTypes().stream()
                .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow()).addEventFilter(javafx.event.ActionEvent.ACTION,e -> {
            try {
                BigDecimal q = new BigDecimal(quantity.getText().replace(',', '.')), p = new BigDecimal(price.getText().replace(',', '.'));
                if (name.getText().isBlank() || unit.getText().isBlank() || q.signum() <= 0 || p.signum() < 0 || q.scale()>3 || p.scale()>2) throw new IllegalArgumentException();
                JsonNode d = discount.getValue(); BigDecimal percent = d == null ? BigDecimal.ZERO : d.path("percentage").decimalValue();
                BigDecimal finalPrice = p.multiply(BigDecimal.valueOf(100).subtract(percent)).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);
                ObjectNode row = JSON.createObjectNode();
                if (newProduct) { ObjectNode n = row.putObject("newProduct"); n.put("name", name.getText().strip()); n.put("unit",unit.getText().strip()); n.put("article",article.getText().strip()); n.put("price",p); }
                else row.set("productId", source.path(index < 0 ? "id" : "productId"));
                row.put("productNameSnapshot",name.getText().strip()); row.put("unitSnapshot",unit.getText().strip()); row.put("articleSnapshot",article.getText().strip());
                row.put("quantity",q); row.put("priceWithoutDiscount",p); row.put("priceWithDiscount",finalPrice); row.put("lineTotal",q.multiply(finalPrice).setScale(2,RoundingMode.HALF_UP)); row.put("supplyStatus",supply.getValue().name());
                if (d != null) { row.set("discountId",d.path("id")); row.put("discountName",d.path("name").asText()); }
                if (newProduct) { rememberPending(row); productOptions.setItems(catalog); }
                if (index < 0) table.getItems().add(row); else table.getItems().set(index,row); updateTotal();
            } catch (RuntimeException ex) { validation.setText("Проверьте название, единицу, количество и цену."); e.consume(); }
        });
        dialog.showAndWait();
    }
}


