package org.bike.fx.workorder;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.bike.client.api.dto.ClientBicycleData;
import org.bike.client.api.dto.ClientContactData;
import org.bike.client.api.dto.ClientData;
import org.bike.client.presentation.ClientDisplay;
import org.bike.workorder.model.ClientSnapshotData;
import org.bike.workorder.model.WorkOrder;
import org.bike.workorder.model.WorkOrderDetails;
import org.bike.workorder.model.WorkOrderDraft;

final class WorkOrderSelectionDialog {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final ButtonType OPEN = new ButtonType("Открыть", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CANCEL = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);

    private WorkOrderSelectionDialog() { }

    static Optional<WorkOrder> show(Window owner, List<WorkOrder> orders, String initialSearch) {
        Dialog<WorkOrder> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Выбор бланка");
        dialog.setHeaderText("Найдено бланков: " + orders.size());
        dialog.getDialogPane().getButtonTypes().setAll(OPEN, CANCEL);
        dialog.getDialogPane().getStyleClass().add("dialog");
        var stylesheet = WorkOrderSelectionDialog.class.getResource("/org/bike/fx/theme.css");
        if (stylesheet != null) dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());

        TextField search = new TextField();
        search.setPromptText("Номер бланка, клиент, телефон или велосипед");
        search.setText(initialSearch);
        Button find = new Button("Найти");
        TableView<WorkOrder> table = new TableView<>();
        table.setPlaceholder(new Label("Бланки не найдены"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().setAll(
                column("Номер бланка", 135, order -> order.number()),
                column("Дата", 105, order -> order.createdAt().format(DATE_FORMAT)),
                column("Клиент", 230, WorkOrderSelectionDialog::clientName),
                column("Велосипед", 200, WorkOrderSelectionDialog::bicycleName));

        FilteredList<WorkOrder> filteredOrders =
                new FilteredList<>(FXCollections.observableArrayList(orders), order -> true);
        table.setItems(filteredOrders);
        Runnable applySearch =
                () -> {
                    String term = normalize(search.getText());
                    filteredOrders.setPredicate(order -> term.isEmpty() || matches(order, term));
                };
        find.setOnAction(event -> applySearch.run());
        search.setOnAction(event -> applySearch.run());
        applySearch.run();

        ContextMenu contextMenu = new ContextMenu();
        MenuItem openMenuItem = new MenuItem("Открыть");
        openMenuItem.setOnAction(event -> closeWithSelection(dialog, table));
        contextMenu.getItems().add(openMenuItem);
        table.setRowFactory(
                view -> {
                    TableRow<WorkOrder> row = new TableRow<>();
                    row.setOnMousePressed(
                            event -> {
                                if (event.getButton() == MouseButton.SECONDARY && !row.isEmpty()) {
                                    table.getSelectionModel().select(row.getItem());
                                }
                            });
                    row.setContextMenu(contextMenu);
                    row.setOnMouseClicked(
                            event -> {
                                if (event.getButton() == MouseButton.PRIMARY
                                        && event.getClickCount() == 2
                                        && !row.isEmpty()) {
                                    closeWithSelection(dialog, table);
                                }
                            });
                    return row;
                });
        table.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.ENTER && table.getSelectionModel().getSelectedItem() != null) {
                        event.consume();
                        closeWithSelection(dialog, table);
                    }
                });

        HBox searchBar = new HBox(8, search, find);
        searchBar.setPadding(new Insets(0, 0, 10, 0));
        HBox.setHgrow(search, Priority.ALWAYS);
        VBox content = new VBox(8, searchBar, table);
        content.setPadding(new Insets(4));
        VBox.setVgrow(table, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(880, 520);
        Button openButton = (Button) dialog.getDialogPane().lookupButton(OPEN);
        openButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        dialog.setResultConverter(button -> button == OPEN ? table.getSelectionModel().getSelectedItem() : null);
        dialog.setOnShown(event -> search.requestFocus());
        return dialog.showAndWait();
    }

    private static TableColumn<WorkOrder, String> column(
            String title, double minWidth, java.util.function.Function<WorkOrder, String> value) {
        TableColumn<WorkOrder, String> column = new TableColumn<>(title);
        column.setMinWidth(minWidth);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private static void closeWithSelection(Dialog<WorkOrder> dialog, TableView<WorkOrder> table) {
        WorkOrder selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            dialog.setResult(selected);
            dialog.close();
        }
    }

    private static boolean matches(WorkOrder order, String term) {
        return searchValues(order).stream().anyMatch(value -> normalize(value).contains(term));
    }

    private static List<String> searchValues(WorkOrder order) {
        WorkOrderDraft draft = order.draft();
        List<String> values = new ArrayList<>();
        values.add(order.number());
        values.add(order.createdAt().format(DATE_FORMAT));
        values.add(clientName(order));
        values.add(bicycleName(order));
        ClientData client = draft.client();
        ClientSnapshotData snapshot = draft.snapshot();
        WorkOrderDetails details = draft.details();
        if (client != null) {
            values.add(ClientDisplay.name(client));
            values.add(client.organizationName());
            values.add(client.representative());
            for (ClientContactData contact : client.contacts()) values.add(contact.value());
        }
        if (snapshot != null) {
            values.add(snapshot.displayName());
            values.add(snapshot.firstName());
            values.add(snapshot.lastName());
            values.add(snapshot.middleName());
            values.add(snapshot.organizationName());
            values.add(snapshot.representative());
            values.add(snapshot.contactValue());
        }
        if (details != null) {
            values.add(details.firstName());
            values.add(details.lastName());
            values.add(details.middleName());
            values.add(details.phone());
        }
        values.add(draft.contactValue());
        return values;
    }

    private static String clientName(WorkOrder order) {
        WorkOrderDraft draft = order.draft();
        ClientSnapshotData snapshot = draft.snapshot();
        if (snapshot != null && !isBlank(snapshot.displayName())) return snapshot.displayName();
        if (draft.client() != null) return ClientDisplay.name(draft.client());
        WorkOrderDetails details = draft.details();
        if (details == null) return "—";
        String name = Stream.of(details.lastName(), details.firstName(), details.middleName())
                .filter(value -> !isBlank(value))
                .collect(Collectors.joining(" "));
        return name.isBlank() ? "—" : name;
    }

    private static String bicycleName(WorkOrder order) {
        WorkOrderDraft draft = order.draft();
        ClientData client = draft.client();
        if (client != null && draft.clientBicycleId() != null) {
            Optional<ClientBicycleData> bicycle = client.bicycles().stream()
                    .filter(value -> Objects.equals(value.id(), draft.clientBicycleId()))
                    .findFirst();
            if (bicycle.isPresent()) {
                return joinDescription(bicycle.get().brandName(), bicycle.get().comment());
            }
        }
        ClientSnapshotData snapshot = draft.snapshot();
        if (snapshot != null && (!isBlank(snapshot.bicycleBrand()) || !isBlank(snapshot.bicycleComment()))) {
            return joinDescription(snapshot.bicycleBrand(), snapshot.bicycleComment());
        }
        WorkOrderDetails details = draft.details();
        return details == null ? "—" : joinDescription(details.bicycleBrand(), details.bicycleComment());
    }

    private static String joinDescription(String first, String second) {
        String result = Stream.of(first, second)
                .filter(value -> !isBlank(value))
                .collect(Collectors.joining(" · "));
        return result.isBlank() ? "—" : result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").strip().toLowerCase(Locale.ROOT);
    }
}
