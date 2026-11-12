package org.bike.fx.workorder;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;
import javafx.util.StringConverter;

import org.bike.catalog.api.dto.WorkshopServiceData;
import org.bike.client.api.dto.*;
import org.bike.client.presentation.ClientDisplay;
import org.bike.fx.*;
import org.bike.fx.client.*;
import org.bike.trello.model.TrelloData.Board;
import org.bike.trello.model.TrelloData.BoardList;
import org.bike.trello.model.TrelloData.Selection;
import org.bike.workorder.model.*;
import org.bike.workorder.model.WorkOrderDraft.ServiceLine;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class WorkOrderController implements SectionController {
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");

    @FXML private javafx.scene.layout.BorderPane root;

    private enum Mode {
        CREATE,
        EDIT
    }

    private enum NumberGeneration {
        AUTOMATIC,
        MANUAL
    }

    private record DiscountChoice(Long id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    @FXML private Label mode, clientName, clientDetails, snapshotStatus, summary, status;
    @FXML private TextField number, time, query;
    @FXML private DatePicker date;
    @FXML private ComboBox<ClientBicycleData> bicycle;
    @FXML private ComboBox<ClientContactData> contact;
    @FXML private ComboBox<WorkshopServiceData> service;
    @FXML private ComboBox<Board> board;
    @FXML private ComboBox<BoardList> list;

    @FXML private ComboBox<org.bike.trello.model.TrelloData.Label> labels;
    @FXML private TableView<ServiceLine> services;
    @FXML private TableColumn<ServiceLine, String> serviceName;
    @FXML private TableColumn<ServiceLine, BigDecimal> price, finalPrice;
    @FXML private TableColumn<ServiceLine, DiscountChoice> discount;
    @FXML private TextArea issue, clientComment, internalComment;
    @FXML private Button newButton, saveButton;
    private ClientBicycleData repairBicycle;
    private SearchableComboBox<ClientBicycleData> bicycleOptions;
    private SearchableComboBox<ClientContactData> contactOptions;
    private SearchableComboBox<WorkshopServiceData> serviceOptions;
    @FXML private javafx.scene.layout.HBox orderLines;
    @FXML private Label grandTotal;
    private ProductsPanel productPanel;
    @FXML private javafx.scene.layout.VBox productsView;
    @FXML private ProductsPanel productsViewController;
    @FXML private CustomerPartsPanel customerPartsViewController;
    private FxContext context;
    private TrelloFields trelloFields;
    private String statusCode = "NEW";
    private ClientData client;
    private ClientSnapshotData snapshot;
    private Long clientVersion, bicycleVersion;
    private Mode currentMode = Mode.CREATE;
    private String originalNumber, action = "KEEP", clean = "";
    private boolean updating, busy, selectionReady = true, manualDate;
    private long selectionRevision;
    private long numberGenerationRevision;
    private boolean defaultCommentExists;
    private Long snapshotBicycleId, snapshotContactId;
    private final javafx.collections.ObservableList<DiscountChoice> discountChoices =
            javafx.collections.FXCollections.observableArrayList();
    private final Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), event -> tick()));
    private final PauseTransition statusTimeout = new PauseTransition(Duration.seconds(4));

    @FXML
    private void editIssue() {
        editComment(issue, "Заявленная неисправность");
    }

    @FXML
    private void editClientComment() {
        editComment(clientComment, "Комментарий к заказу");
    }

    @FXML
    private void editInternalComment() {
        editComment(internalComment, "Комментарий для себя");
    }

    private void editComment(TextArea source, String title) {
        try {
            var loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/org/bike/fx/CommentEditor.fxml"));
            var pane = (DialogPane) loader.load();
            var editor = (CommentEditorController) loader.getController();
            editor.setText(source.getText());

            var dialog = new Dialog<ButtonType>();
            dialog.setTitle(title);
            dialog.initOwner(root.getScene().getWindow());
            dialog.setResizable(true);
            dialog.setDialogPane(pane);
            dialog.setOnShown(event -> editor.focusText());
            dialog.showAndWait()
                    .filter(pane.getButtonTypes().getFirst()::equals)
                    .ifPresent(result -> source.setText(editor.text()));
        } catch (java.io.IOException exception) {
            error(exception);
        }
    }

    @FXML
    private void printAcceptance() {
        if (busy) {
            error(new IllegalStateException("Дождитесь завершения обновления данных бланка."));
            return;
        }
        if (client == null) {
            error(new IllegalStateException("Сначала выберите клиента."));
            return;
        }

        var customerParts = customerPartsViewController.items();
        boolean hasBicycle = repairBicycle != null;
        if (!hasBicycle && customerParts.isEmpty()) {
            error(new IllegalStateException("Добавьте велосипед или деталь клиента."));
            return;
        }

        try {
            var lines = acceptanceLines(customerParts);
            BigDecimal serviceTotal = services.getItems().stream()
                    .map(line -> line.priceWithDiscount() == null
                            ? Objects.requireNonNullElse(line.priceWithoutDiscount(), BigDecimal.ZERO)
                            : line.priceWithDiscount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal total = serviceTotal.add(productPanel.amount());
            String dateText = date.getValue() == null
                    ? ""
                    : date.getValue().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            if (time.getText() != null && !time.getText().isBlank())
                dateText = (dateText + " " + time.getText().strip()).strip();

            String itemType = hasBicycle
                    ? customerParts.isEmpty() ? "Велосипед" : "Велосипед и запчасти"
                    : customerParts.size() == 1 ? "Запчасть" : "Запчасти";
            var itemNames = new ArrayList<String>();
            if (hasBicycle) itemNames.add("Велосипед: " + bicycleText(repairBicycle));
            customerParts.forEach(part -> itemNames.add(customerPartDescription(part)));
            String giverLabel = signatureLabel(hasBicycle, customerParts.size(), true);
            String receiverLabel = signatureLabel(hasBicycle, customerParts.size(), false);

            var data = new AcceptanceActPrinter.Data(
                    dateText,
                    clientName.getText(),
                    selectedContactPhone(),
                    itemType,
                    String.join("\n", itemNames),
                    issue.getText(),
                    clientComment.getText(),
                    formatAmount(total),
                    giverLabel,
                    receiverLabel,
                    number.getText() == null ? "" : number.getText().strip(),
                    lines);
            var printer = new AcceptanceActPrinter();
            var file = printer.generate(data);
            printer.openForPrinting(file);
            showInfo("Акт приёма сформирован и открыт для печати.");
        } catch (Exception exception) {
            error(exception);
        }
    }

    @FXML
    private void printIssue() {
        if (busy) {
            error(new IllegalStateException("Дождитесь завершения обновления данных бланка."));
            return;
        }
        if (client == null) {
            error(new IllegalStateException("Сначала выберите клиента."));
            return;
        }

        var customerParts = customerPartsViewController.items();
        boolean hasBicycle = repairBicycle != null;
        if (!hasBicycle && customerParts.isEmpty()) {
            error(new IllegalStateException("Добавьте велосипед или деталь клиента."));
            return;
        }

        try {
            BigDecimal serviceTotal = services.getItems().stream()
                    .map(line -> line.priceWithDiscount() == null
                            ? Objects.requireNonNullElse(line.priceWithoutDiscount(), BigDecimal.ZERO)
                            : line.priceWithDiscount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal total = serviceTotal.add(productPanel.amount());
            String itemLabel = hasBicycle
                    ? customerParts.isEmpty() ? "Велосипед:" : "Велосипед и запчасти:"
                    : customerParts.size() == 1 ? "Запчасть:" : "Запчасти:";
            var itemDescriptions = new ArrayList<String>();
            if (hasBicycle) itemDescriptions.add(bicycleText(repairBicycle));
            customerParts.forEach(part -> itemDescriptions.add(customerPartDescription(part).replaceFirst("^Запчасть: ", "")));
            String itemDescription = String.join("; ", itemDescriptions);
            String acceptedItem = hasBicycle
                    ? customerParts.isEmpty() ? "велосипед" : "велосипед и запчасти"
                    : customerParts.size() == 1 ? "запчасть" : "запчасти";
            String actTitle = hasBicycle
                    ? customerParts.isEmpty()
                            ? "АКТ ВЫДАЧИ ВЕЛОСИПЕДА ИЗ РЕМОНТА"
                            : "АКТ ВЫДАЧИ ВЕЛОСИПЕДА И ЗАПЧАСТЕЙ ИЗ РЕМОНТА"
                    : "АКТ ВЫДАЧИ ЗАПЧАСТЕЙ ИЗ РЕМОНТА";
            var data = new IssueActPrinter.Data(
                    actTitle,
                    number.getText() == null ? "" : number.getText().strip(),
                    LocalDate.now(),
                    clientName.getText(),
                    selectedContactPhone(),
                    itemLabel,
                    itemDescription,
                    "Клиент принял " + acceptedItem + ". С объёмом и качеством выполненных работ ознакомлен. "
                            + "Претензий по внешнему виду и комплектности не имеет.",
                    formatAmount(total),
                    acceptanceLines(customerParts));
            var printer = new IssueActPrinter();
            var file = printer.generate(data);
            printer.openForPrinting(file);
            showInfo("Акт выдачи сформирован и открыт для печати.");
        } catch (Exception exception) {
            error(exception);
        }
    }
    private List<AcceptanceActPrinter.Line> acceptanceLines(
            List<com.fasterxml.jackson.databind.JsonNode> customerParts) {
        var lines = new ArrayList<AcceptanceActPrinter.Line>();
        services.getItems().forEach(
                serviceLine -> {
                    BigDecimal price = serviceLine.priceWithDiscount() == null
                            ? Objects.requireNonNullElse(serviceLine.priceWithoutDiscount(), BigDecimal.ZERO)
                            : serviceLine.priceWithDiscount();
                    lines.add(new AcceptanceActPrinter.Line(
                            serviceLine.serviceNameSnapshot(), "1 усл", formatAmount(price), formatAmount(price)));
                });
        productPanel.items().forEach(
                product -> {
                    BigDecimal quantity = product.path("quantity").decimalValue();
                    BigDecimal price = product.path("priceWithDiscount").isNumber()
                            ? product.path("priceWithDiscount").decimalValue()
                            : product.path("priceWithoutDiscount").decimalValue();
                    BigDecimal amount = product.path("lineTotal").isNumber()
                            ? product.path("lineTotal").decimalValue()
                            : quantity.multiply(price);
                    String unit = product.path("unitSnapshot").asText("").strip();
                    String quantityText = formatQuantity(quantity)
                            + (unit.isBlank() ? "" : " " + unit);
                    lines.add(new AcceptanceActPrinter.Line(
                            product.path("productNameSnapshot").asText("Товар"),
                            quantityText,
                            formatAmount(price),
                            formatAmount(amount)));
                });
        return List.copyOf(lines);
    }

    private String customerPartDescription(com.fasterxml.jackson.databind.JsonNode part) {
        String description = part.path("name").asText("Запчасть");
        String brand = part.path("brand").asText("").strip();
        String model = part.path("model").asText("").strip();
        String details = String.join(" ", java.util.stream.Stream.of(brand, model)
                .filter(value -> !value.isBlank()).toList());
        if (!details.isBlank()) description += " (" + details + ")";
        var quantity = part.path("quantity").decimalValue();
        if (quantity.compareTo(BigDecimal.ONE) != 0)
            description += " · кол-во " + formatQuantity(quantity);
        return "Запчасть: " + description;
    }

    private String selectedContactPhone() {
        var selected = contact.getValue();
        if (selected == null) return "";
        if (snapshot != null && Objects.equals(snapshotContactId, selected.id())
                && snapshot.contactValue() != null) return snapshot.contactValue();
        return Objects.toString(selected.value(), "");
    }

    private String signatureLabel(boolean hasBicycle, int partCount, boolean giver) {
        String action = giver ? "сдал" : "принял";
        if (hasBicycle && partCount > 0) return "Велосипед и запчасти " + action;
        if (hasBicycle) return "Велосипед " + action;
        return (partCount == 1 ? "Запчасть " : "Запчасти ") + action;
    }

    private String formatAmount(BigDecimal amount) {
        var format = java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private String formatQuantity(BigDecimal quantity) {
        var format = java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(3);
        return format.format(quantity);
    }


    @FXML
    private void initialize() {
        serviceName.setCellValueFactory(
                c -> new ReadOnlyObjectWrapper<>(c.getValue().serviceNameSnapshot()));
        price.setCellValueFactory(
                c -> new ReadOnlyObjectWrapper<>(c.getValue().priceWithoutDiscount()));
        finalPrice.setCellValueFactory(
                c -> new ReadOnlyObjectWrapper<>(c.getValue().priceWithDiscount()));
        discount.setCellValueFactory(
                c ->
                        new ReadOnlyObjectWrapper<>(
                                discountChoices.stream()
                                        .filter(
                                                d ->
                                                        Objects.equals(
                                                                d.id(), c.getValue().discountId()))
                                        .findFirst()
                                        .orElse(
                                                new DiscountChoice(
                                                        c.getValue().discountId(),
                                                        Objects.toString(
                                                                c.getValue().discountName(),
                                                                "Без скидки")))));
        discount.setCellFactory(
                column ->
                        new TableCell<>() {
                            private final ComboBox<DiscountChoice> editor = new ComboBox<>();
                            private final SearchableComboBox<DiscountChoice> options =
                                    SearchableComboBox.install(editor, DiscountChoice::name);

                            {
                                options.onSelection(() -> commitEdit(editor.getValue()));
                                options.onCommit(() -> commitEdit(editor.getValue()));
                            }

                            @Override
                            public void startEdit() {
                                if (isEmpty()) return;
                                super.startEdit();
                                options.setItems(discountChoices);
                                editor.setValue(getItem());
                                setText(null);
                                setGraphic(editor);
                                editor.show();
                            }

                            @Override
                            public void cancelEdit() {
                                super.cancelEdit();
                                setGraphic(null);
                                setText(getItem() == null ? "" : getItem().name());
                            }

                            @Override
                            protected void updateItem(DiscountChoice value, boolean empty) {
                                super.updateItem(value, empty);
                                if (empty) {
                                    setText(null);
                                    setGraphic(null);
                                } else if (isEditing()) {
                                    editor.setValue(value);
                                    setText(null);
                                    setGraphic(editor);
                                } else {
                                    setGraphic(null);
                                    setText(value == null ? "" : value.name());
                                }
                            }
                        });
        price.setCellFactory(
                TextFieldTableCell.forTableColumn(
                        new StringConverter<>() {
                            @Override
                            public String toString(BigDecimal value) {
                                return value == null ? "" : value.stripTrailingZeros().toPlainString();
                            }

                            @Override
                            public BigDecimal fromString(String value) {
                                try {
                                    return new BigDecimal(value.trim().replace(',', '.'));
                                } catch (NumberFormatException | NullPointerException exception) {
                                    return null;
                                }
                            }
                        }));
        price.setEditable(true);
        price.setOnEditCommit(
                event -> {
                    BigDecimal newPrice = event.getNewValue();
                    if (newPrice == null || newPrice.signum() < 0 || newPrice.scale() > 2) {
                        showWarning("Введите цену от 0 с точностью до двух знаков.");
                        services.refresh();
                        return;
                    }
                    ServiceLine old = event.getRowValue();
                    var lines = new ArrayList<>(services.getItems());
                    lines.set(
                            event.getTablePosition().getRow(),
                            new ServiceLine(
                                    old.serviceId(),
                                    old.discountId(),
                                    null,
                                    old.serviceNameSnapshot(),
                                    newPrice,
                                    null,
                                    null));
                    quote(lines);
                });
        services.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        discount.setOnEditCommit(
                event -> {
                    var old = event.getRowValue();
                    var lines = new ArrayList<>(services.getItems());
                    lines.set(
                            event.getTablePosition().getRow(),
                            new ServiceLine(
                                    old.serviceId(),
                                    event.getNewValue().id(),
                                    null,
                                    old.serviceNameSnapshot(),
                                    old.priceWithoutDiscount(),
                                    null,
                                    null));
                    quote(lines);
                });
        services.setEditable(true);
        services.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.DELETE) {
                        removeService();
                        event.consume();
                    }
                });
        var remove = new MenuItem("Удалить услугу");
        remove.setOnAction(event -> removeService());
        services.setContextMenu(new ContextMenu(remove));
        bicycleOptions = SearchableComboBox.install(bicycle, this::bicycleText);
        bicycleOptions.onCommit(() -> {
            if (bicycle.getValue() == null) return;
            repairBicycle = bicycle.getValue();
            syncRepairBicycle();
            preview();
            javafx.application.Platform.runLater(bicycleOptions::clear);
        });
        contactOptions = SearchableComboBox.install(contact, this::contactText);
        bicycle.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(ClientBicycleData value, boolean empty) {
                super.updateItem(value, empty);setText(empty || value == null ? null : bicycleText(value));
            }
        });
        contact.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(ClientContactData value, boolean empty) {
                super.updateItem(value, empty);setText(empty || value == null ? null : contactText(value));
            }
        });
        serviceOptions = SearchableComboBox.install(service, WorkshopServiceData::name);
        serviceOptions.onCommit(this::addService);
        bicycle.valueProperty()
                .addListener(
                        (o, a, b) -> {
                            // Выбор подтверждается Enter и добавляет велосипед в таблицу.
                        });
        contact.valueProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!updating) preview();
                        });
        clock.setCycleCount(Timeline.INDEFINITE);
        date.setValue(LocalDate.now());
        time.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        date.valueProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!updating) manualDate = true;
                        });
        time.textProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!updating) manualDate = true;
                        });
    }

    public void bind(FxContext value) {
        context = value;
        customerPartsViewController.onBicycleRemoved(() -> { repairBicycle = null; bicycleOptions.clear(); preview(); });
        productPanel = productsViewController;
        productPanel.bind(context, this::error, this::summary);
        javafx.scene.layout.HBox.setHgrow(productsView, javafx.scene.layout.Priority.ALWAYS);
        productsView.prefWidthProperty().bind(orderLines.widthProperty().subtract(12).divide(2));
        ((javafx.scene.layout.Region) services.getParent()).prefWidthProperty().bind(orderLines.widthProperty().subtract(12).divide(2));
        trelloFields =
                new TrelloFields(
                        new TrelloFields.View(board, list, labels), context.trello, this::error);
        clock.play();
        newDraft();
    }

    @Override
    public void dispose() {
        clock.stop();
        if (trelloFields != null) trelloFields.close();
    }

    public void refresh() {
        if (context == null || !context.connection.connectedProperty().get()) return;
        context.catalog.listServices(serviceOptions::setItems, this::error);
        context.discounts.list(
                values -> {
                    discountChoices.setAll(new DiscountChoice(null, "Без скидки"));
                    values.forEach(
                            d ->
                                    discountChoices.add(
                                            new DiscountChoice(
                                                    d.id(),
                                                    d.name() + " · " + d.percentage() + " %")));
                },
                this::error);
        refreshTrello();
    }

    private void tick() {
        if (currentMode == Mode.CREATE && !manualDate) {
            updating = true;
            date.setValue(LocalDate.now());
            time.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            updating = false;
        }
    }

    @FXML
    private void create() {
        if (canLeave()) newDraft();
    }

    private void newDraft() {
        currentMode = Mode.CREATE;
        statusCode = "NEW";
        originalNumber = null;
        client = null;
        snapshot = null;
        clientVersion = null;
        bicycleVersion = null;
        action = "KEEP";
        selectionRevision++;
        selectionReady = true;
        updating = true;
        number.clear();
        query.clear();
        bicycleOptions.setItems(List.of());
        contactOptions.setItems(List.of());
        repairBicycle = null;
        customerPartsViewController.bicycle(null, "", "");
        customerPartsViewController.items(List.of());
        services.getItems().clear();
        if (productPanel != null) productPanel.items(List.of());
        issue.clear();
        clientComment.clear();
        internalComment.clear();
        trelloFields.reset();
        updating = false;
        manualDate = false;
        tick();
        displayClient();
        setMode("Создание нового бланка", "work-order-create");
        newButton.setDisable(true);
        summary();
        clean = fingerprint();
        generateNumberAutomatically();
        refreshTrello();
        long draftRevision = selectionRevision;
        context.defaultOrderComment.read(value -> {
            if (draftRevision == selectionRevision && currentMode == Mode.CREATE
                    && clientComment.getText().isBlank() && value != null) {
                defaultCommentExists = true;
                clientComment.setText(value);
                clean = fingerprint();
            } else if (draftRevision == selectionRevision && value == null) {
                defaultCommentExists = false;
            }
        }, this::error);
    }

    @FXML
    private void generateNumber() {
        requestNumber(NumberGeneration.MANUAL);
    }

    private void generateNumberAutomatically() {
        requestNumber(NumberGeneration.AUTOMATIC);
    }

    private void requestNumber(NumberGeneration generation) {
        if (context == null || busy) return;
        long requestRevision = ++numberGenerationRevision;
        context.numbers.generate(
                generatedNumber -> {
                    if (requestRevision != numberGenerationRevision) return;
                    if (generation == NumberGeneration.AUTOMATIC
                            && (currentMode != Mode.CREATE || !number.getText().isBlank())) return;
                    boolean draftWasClean = clean.equals(fingerprint());
                    number.setText(generatedNumber);
                    if (generation == NumberGeneration.AUTOMATIC && draftWasClean)
                        clean = fingerprint();
                },
                this::error);
    }

    @FXML
    private void open() {
        if (context == null || !canLeave()) return;
        var dialog = new TextInputDialog();
        dialog.setTitle("Открыть бланк");
        dialog.setHeaderText("Введите код бланка");
        dialog.initOwner(number.getScene().getWindow());
        dialog.showAndWait()
                .filter(v -> !v.isBlank())
                .ifPresent(
                        value -> {
                            setBusy(true);
                            context.orders.load(
                                    value,
                                    orders -> {
                                        setBusy(false);
                                        if (orders.isEmpty()) {
                                            error(new IllegalStateException("Бланки с таким номером не найдены."));
                                            return;
                                        }
                                        var owner = number.getScene().getWindow();
                                        if (orders.size() == 1) {
                                            openOrder(orders.getFirst());
                                        } else {
                                            WorkOrderSelectionDialog.show(owner, orders, value)
                                                    .ifPresent(this::openOrder);
                                        }
                                    },
                                    error -> {
                                        setBusy(false);
                                        error(error);
                                    });
                        });
    }

    private void openOrder(WorkOrder order) {
        display(order);
        if (client != null) {
            setBusy(true);
            refreshClientAndPreview(
                    () -> {
                        setBusy(false);
                        clean = fingerprint();
                    },
                    error -> {
                        setBusy(false);
                        error(error);
                    });
        }
    }

    public void openClientWorkOrder(String number) {
        if (context == null || !canLeave()) return;
        setBusy(true);
        context.orders.load(
                number,
                orders -> {
                    setBusy(false);
                    if (orders.isEmpty()) {
                        error(new IllegalStateException("Бланк " + number + " не найден."));
                    } else {
                        openOrder(orders.getFirst());
                    }
                },
                error -> {
                    setBusy(false);
                    error(error);
                });
    }

    private void display(WorkOrder order) {
        var draft = order.draft();
        currentMode = Mode.EDIT;
        statusCode = draft.statusCode();
        originalNumber = order.number();
        client = draft.client();
        snapshot = draft.snapshot();
        clientVersion = client == null ? null : client.version();
        action = "KEEP";
        selectionRevision++;
        selectionReady = true;
        updating = true;
        number.setText(order.number());
        date.setValue(order.createdAt().toLocalDate());
        time.setText(order.createdAt().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        services.getItems().setAll(draft.services());
        productPanel.items(draft.products());
        customerPartsViewController.items(draft.customerParts());
        issue.setText(draft.reportedIssue());
        clientComment.setText(draft.clientComment());
        internalComment.setText(draft.internalComment());
        repairBicycle = client == null ? null : client.bicycles().stream().filter(b -> b.id().equals(draft.clientBicycleId())).findFirst().orElse(null);
        choices(draft.clientBicycleId(), draft.clientContactId());
        syncRepairBicycle();
        customerPartsViewController.bicycleCondition(draft.bicycleConditionOnReceipt());
        snapshotBicycleId = draft.clientBicycleId();
        snapshotContactId = draft.clientContactId();
        updating = false;
        manualDate = true;
        setMode("Редактирование · № " + order.number(), "work-order-edit");
        newButton.setDisable(false);
        displayClient();
        summary();
        clean = fingerprint();
        trelloFields.open(order.number(), order.trelloCardId());
    }

    private void choices(Long bikeId, Long contactId) {
        bicycleOptions.setItems(client == null ? List.of() : client.bicycles());
        contactOptions.setItems(client == null ? List.of() : client.contacts());
        bicycle.setValue(
                bicycle.getItems().stream()
                        .filter(b -> b.id().equals(bikeId))
                        .findFirst()
                        .orElse(null));
        contact.setValue(
                contact.getItems().stream()
                        .filter(c -> c.id().equals(contactId))
                        .findFirst()
                        .orElse(null));
        if (repairBicycle != null) repairBicycle = client == null ? null : client.bicycles().stream()
                .filter(b -> b.id().equals(repairBicycle.id())).findFirst().orElse(null);
        syncRepairBicycle();
        bicycleVersion = repairBicycle == null ? null : repairBicycle.version();
    }

    private void setMode(String text, String styleClass) {
        mode.setText(text);
        mode.getStyleClass().removeAll("work-order-create", "work-order-edit");
        mode.getStyleClass().add(styleClass);
    }

    @FXML
    private void search() {
        if (context == null || busy) return;
        context.clients.search(
                query.getText(),
                result -> {
                    if (isSingleUnambiguousMatch(result)) {
                        var value = result.all().getFirst();
                        select(value);
                        if (value.comment() != null && !value.comment().isBlank()) {
                            var dialog =
                                    FxView.confirmation(
                                            "У клиента есть комментарий. Показать?",
                                            ButtonType.YES,
                                            ButtonType.NO);
                            dialog.initOwner(number.getScene().getWindow());
                            if (dialog.showAndWait().orElse(ButtonType.NO) == ButtonType.YES)
                                new Alert(Alert.AlertType.INFORMATION, value.comment())
                                        .showAndWait();
                        }
                    } else if (result.all().isEmpty()) {
                        offerCreateClient();
                    } else {
                        picker();
                    }
                },
                this::error);
    }

    private boolean isSingleUnambiguousMatch(ClientSearchResult result) {
        return result.exact().size() == 1 && result.similar().isEmpty();
    }

    private void offerCreateClient() {
        var dialog =
                FxView.confirmation(
                        "Клиент не найден. Создать новую карточку клиента?",
                        ButtonType.YES,
                        ButtonType.NO);
        dialog.setHeaderText("Совпадений не найдено");
        dialog.initOwner(number.getScene().getWindow());
        if (dialog.showAndWait().orElse(ButtonType.NO) == ButtonType.YES)
            card(null, true, query.getText());
    }

    @FXML
    private void picker() {
        if (context == null || busy) return;
        FxView.Loaded<ClientsController> view = FxView.load("Clients");
        var stage = FxView.dialog(number.getScene().getWindow(), "Выбор клиента", view.root());
        view.controller().bind(context);
        view.controller()
                .picker(
                        value -> {
                            select(value);
                            stage.close();
                        },
                        query.getText());
        stage.showAndWait();
    }

    private void select(ClientData value) {
        query.clear();
        client = value;
        snapshot = null;
        updating = true;
        Long firstBicycleId =
                value.bicycles().isEmpty() ? null : value.bicycles().getFirst().id();
        Long firstContactId =
                value.contacts().isEmpty() ? null : value.contacts().getFirst().id();
        choices(firstBicycleId, firstContactId);
        updating = false;
        displayClient();
        preview();
    }

    @FXML
    private void createClient() {
        card(null, true, query.getText());
    }

    @FXML
    private void openClient() {
        if (client != null)
            context.clients.find(client.id(), value -> card(value, false, true), this::error);
    }

    private void card(ClientData value, boolean choose) {
        card(value, choose, null, false);
    }

    private void card(ClientData value, boolean choose, String nameInput) {
        card(value, choose, nameInput, false);
    }

    private void card(ClientData value, boolean choose, boolean startEditing) {
        card(value, choose, null, startEditing);
    }

    private void card(ClientData value, boolean choose, String nameInput, boolean startEditing) {
        Long selectedBicycleId = repairBicycle == null ? null : repairBicycle.id();
        Long selectedContactId = contact.getValue() == null ? null : contact.getValue().id();
        var previousBicycleIds = value == null ? java.util.Set.<Long>of()
                : value.bicycles().stream().map(ClientBicycleData::id)
                        .collect(java.util.stream.Collectors.toSet());
        var previousContactIds = value == null ? java.util.Set.<Long>of()
                : value.contacts().stream().map(ClientContactData::id)
                        .collect(java.util.stream.Collectors.toSet());
        FxView.Loaded<ClientEditorController> view = FxView.load("ClientEditor");
        var stage = FxView.dialog(number.getScene().getWindow(), "Карточка клиента", view.root());
        view.controller()
                .bind(
                        context,
                        value,
                        saved -> {
                            if (choose) select(saved);
                            else {
                                Long bicycleId = saved.bicycles().stream()
                                        .map(ClientBicycleData::id)
                                        .filter(id -> !previousBicycleIds.contains(id))
                                        .findFirst().orElse(selectedBicycleId);
                                Long contactId = saved.contacts().stream()
                                        .map(ClientContactData::id)
                                        .filter(id -> !previousContactIds.contains(id))
                                        .findFirst().orElse(selectedContactId);
                                client = saved;
                                updating = true;
                                try {
                                    choices(bicycleId, contactId);
                                } finally {
                                    updating = false;
                                }
                                displayClient();
                                preview();
                                showInfo("Карточка клиента обновлена.");
                            }
                        },
                        nameInput);
        if (startEditing) view.controller().startEditing();
        stage.setOnCloseRequest(
                event -> {
                    if (!view.controller().canLeave()) event.consume();
                });
        stage.showAndWait();
    }

    @FXML
    private void refreshClient() {
        if (context == null || client == null || busy) return;
        setBusy(true);
        refreshClientAndPreview(
                () -> setBusy(false),
                error -> {
                    setBusy(false);
                    error(error);
                });
    }

    private void preview() {
        preview(null, this::error);
    }

    private void preview(Runnable onReady, java.util.function.Consumer<Exception> onFailure) {
        if (context == null || client == null) return;
        long token = ++selectionRevision;
        selectionReady = false;
        var selectedBicycle = repairBicycle;
        var selectedContact = contact.getValue();
        context.clients.preview(
                client.id(),
                selectedBicycle == null ? null : selectedBicycle.id(),
                selectedContact == null ? null : selectedContact.id(),
                value -> {
                    if (selectionRevision != token) return;
                    snapshot = value.snapshot();
                    snapshotBicycleId = selectedBicycle == null ? null : selectedBicycle.id();
                    snapshotContactId = selectedContact == null ? null : selectedContact.id();
                    clientVersion = value.expectedClientVersion();
                    bicycleVersion = value.expectedBicycleVersion();
                    action = "REFRESH";
                    selectionReady = true;
                    displayClient();
                    if (onReady != null) onReady.run();
                },
                error -> {
                    if (selectionRevision == token) onFailure.accept(error);
                });
    }

    private void refreshClientAndPreview(
            Runnable onReady, java.util.function.Consumer<Exception> onFailure) {
        if (client == null) {
            onFailure.accept(new IllegalStateException("Сначала выберите клиента для бланка."));
            return;
        }
        Long clientId = client.id();
        Long bicycleId = repairBicycle == null ? null : repairBicycle.id();
        Long contactId = contact.getValue() == null ? null : contact.getValue().id();
        context.clients.find(
                clientId,
                value -> {
                    client = value;
                    updating = true;
                    choices(bicycleId, contactId);
                    updating = false;
                    if (bicycleId != null && repairBicycle == null) {
                        onFailure.accept(
                                new IllegalStateException(
                                        "Выбранный велосипед не найден в актуальной карточке"
                                                + " клиента."));
                        return;
                    }
                    displayClient();
                    preview(onReady, onFailure);
                },
                onFailure);
    }

    private void displayClient() {
        bicycle.getButtonCell().setText(repairBicycle == null ? null : bicycleText(repairBicycle));
        contact.getButtonCell().setText(contact.getValue() == null ? null : contactText(contact.getValue()));
        clientName.setText(
                snapshot != null
                        ? snapshot.displayName()
                        : client == null ? "Клиент не выбран" : ClientDisplay.name(client));
        clientDetails.setText(
                client == null
                        ? "Найдите клиента по телефону, ФИО или организации"
                        : "№ "
                                + client.publicId()
                                + " · "
                                + ("ORGANIZATION".equals(client.type())
                                        ? "Организация"
                                        : "Физическое лицо")
                                + (snapshot != null && snapshot.representative() != null
                                        ? "\nПредставитель: " + snapshot.representative()
                                        : ""));
        if (snapshotStatus != null) {
            boolean showSavedSnapshot = !"REFRESH".equals(action);
            snapshotStatus.setVisible(showSavedSnapshot);
            snapshotStatus.setManaged(showSavedSnapshot);
            if (showSavedSnapshot) snapshotStatus.setText("Сохранённые данные бланка");
        }
    }

    private String bicycleText(ClientBicycleData value) {
        if (snapshot == null || !Objects.equals(snapshotBicycleId, value.id())) return ClientDisplay.bicycle(value);
        return Objects.toString(snapshot.bicycleBrand(), "")
                + (snapshot.bicycleComment() == null || snapshot.bicycleComment().isBlank() ? "" : " · " + snapshot.bicycleComment());
    }

    private String contactText(ClientContactData value) {
        if (snapshot == null || !Objects.equals(snapshotContactId, value.id())) return ClientDisplay.contact(value);
        String type = Objects.equals(value.typeCode(), snapshot.contactTypeCode()) ? value.typeName() : snapshot.contactTypeCode();
        return Objects.toString(type, "Контакт") + ": " + Objects.toString(snapshot.contactValue(), "");
    }

    @FXML
    private void addService() {
        if (context == null || busy) return;
        var selected = service.getValue();
        if (selected == null) return;
        var lines = new ArrayList<>(services.getItems());
        lines.add(
                new ServiceLine(
                        selected.id(), null, null, selected.name(), selected.price(), null, null));
        quote(lines, serviceOptions::clear);
    }

    @FXML
    private void removeService() {
        int row = services.getSelectionModel().getSelectedIndex();
        if (row < 0 || busy) return;
        var lines = new ArrayList<>(services.getItems());
        lines.remove(row);
        quote(lines);
    }

    private void quote(List<ServiceLine> lines) {
        quote(lines, () -> {});
    }

    private void quote(List<ServiceLine> lines, Runnable onSuccess) {
        if (context == null || busy) return;
        busy = true;
        context.quotes.calculate(
                lines,
                value -> {
                    services.getItems().setAll(value.services());
                    busy = false;
                    summary();
                    onSuccess.run();
                },
                error -> {
                    busy = false;
                    services.refresh();
                    error(error);
                });
    }

    private void summary() {
        if (grandTotal != null) {
            var serviceAmount = services.getItems().stream().map(ServiceLine::priceWithDiscount)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            grandTotal.setText("Итого по бланку: " + serviceAmount.add(productPanel == null ? BigDecimal.ZERO : productPanel.amount()).toPlainString() + " ₽");
        }
        var total =
                services.getItems().stream()
                        .map(ServiceLine::priceWithoutDiscount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        var finalTotal =
                services.getItems().stream()
                        .map(ServiceLine::priceWithDiscount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.setText(
                "Всего услуг: "
                + services.getItems().size()
                        + "     Стоимость: "
                        + total.toPlainString()
                        + "     Со скидкой: "
                        + finalTotal.toPlainString());
    }

    @FXML
    private void refreshTrello() {
        if (context != null && context.connection.connectedProperty().get()) trelloFields.refresh();
    }

    @FXML
    private void save() {
        if (context == null || busy) return;
        if (client == null) {
            error(new IllegalStateException("Для сохранения выберите клиента."));
            return;
        }
        if (repairBicycle == null && customerPartsViewController.items().isEmpty()) {
            error(new IllegalStateException("Добавьте велосипед или деталь клиента."));
            return;
        }
        Selection pendingTrello;
        try {
            pendingTrello = trelloFields.selection();
        } catch (Exception error) {
            error(error);
            return;
        }
        setBusy(true);
        refreshClientAndPreview(
                () -> {
                    setBusy(false);
                    submitSave(pendingTrello);
                },
                error -> {
                    setBusy(false);
                    error(error);
                });
    }

    private void submitSave(Selection pendingTrello) {
        try {
            var request = draft();
            setBusy(true);
            java.util.function.Consumer<WorkOrder> saved =
                    order -> {
                        if (pendingTrello.listId() == null) {
                            setBusy(false);
                            display(order);
                            showInfo("Бланк сохранён.");
                            return;
                        }
                        context.trello.synchronize(
                                order.number(),
                                pendingTrello,
                                link -> {
                                    setBusy(false);
                                    display(
                                            new WorkOrder(
                                                    order.number(),
                                                    order.createdAt(),
                                                    order.draft(),
                                                    link.cardId()));
                                    showInfo("Бланк и Trello сохранены.");
                                },
                                failure -> {
                                    setBusy(false);
                                    display(order);
                                    trelloFields.restore(pendingTrello);
                                    error(
                                            new IllegalStateException(
                                                    "Бланк сохранён. Не удалось обновить Trello: "
                                                            + failure.getMessage()));
                                });
                    };
            java.util.function.Consumer<Exception> failure =
                    error -> {
                        setBusy(false);
                        error(error);
                    };
            if (currentMode == Mode.CREATE) context.orders.create(request, saved, failure);
            else context.orders.update(originalNumber, request, saved, failure);
        } catch (Exception error) {
            setBusy(false);
            error(error);
        }
    }

    private WorkOrderDraft draft() {
        var bike = repairBicycle;
        var selectedContact = contact.getValue();
        String created =
                LocalDateTime.of(date.getValue(), LocalTime.parse(time.getText()))
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toString();
        return new WorkOrderDraft(
                client == null ? null : client.id(),
                bike == null ? null : bike.id(),
                selectedContact == null ? null : selectedContact.id(),
                issue.getText(),
                clientComment.getText(),
                internalComment.getText(),
                null,
                null,
                List.copyOf(services.getItems()),
                statusCode,
                null,
                number.getText(),
                created,
                client,
                snapshot,
                action,
                clientVersion,
                bicycleVersion, productPanel.items(), customerPartsViewController.items(), customerPartsViewController.bicycleCondition());
    }

    @FXML
    private void clearRepairBicycle() { repairBicycle = null; syncRepairBicycle(); bicycleOptions.clear(); preview(); }

    private void syncRepairBicycle() {
        if (customerPartsViewController != null) customerPartsViewController.bicycle(
                repairBicycle == null ? null : repairBicycle.id(),
                repairBicycle == null ? "" : bicycleText(repairBicycle),
                repairBicycle == null ? "" : repairBicycle.comment());
    }

    private String fingerprint() {
        return number.getText()
                + "|"
                + (client == null ? null : client.id())
                + "|"
                + repairBicycle
                + "|"
                + contact.getValue()
                + "|"
                + action
                + "|"
                + issue.getText()
                + "|"
                + clientComment.getText()
                + "|"
                + internalComment.getText()
                + "|"
                + (productPanel == null ? "" : productPanel.items())
                + customerPartsViewController.bicycleCondition()
                + customerPartsViewController.items()
                + services.getItems()
                + "|"
                + (manualDate ? date.getValue() + " " + time.getText() : "");
    }

    public boolean canLeave() {
        if (busy) return false;
        if (clean.equals(fingerprint()) && (trelloFields == null || !trelloFields.isDirty()))
            return true;
        var dialog =
                FxView.confirmation(
                        "Есть несохранённые изменения бланка. Продолжить без сохранения?",
                        ButtonType.YES,
                        ButtonType.NO);
        dialog.initOwner(number.getScene().getWindow());
        return dialog.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void setBusy(boolean value) {
        busy = value;
        root.setDisable(value);
    }

    private void error(Exception error) {
        showWarning(error.getMessage());
    }

    private void showInfo(String message) {
        status.pseudoClassStateChanged(WARNING, false);
        showStatus(message);
    }

    private void showWarning(String message) {
        status.pseudoClassStateChanged(WARNING, true);
        showStatus(message);
    }

    private void showStatus(String message) {
        statusTimeout.stop();
        status.setText(message);
        statusTimeout.setOnFinished(
                event -> {
                    status.setText("");
                    status.pseudoClassStateChanged(WARNING, false);
                });
        statusTimeout.playFromStart();
    }
}





