package org.bike.fx.catalog;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;

import org.bike.catalog.api.dto.*;
import org.bike.fx.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class CatalogController implements SectionController {
    private static final int SERVICES_PAGE_SIZE = 50;

    private enum FormMode { VIEW, CREATE, EDIT }

    @FXML private ListView<WorkshopServiceData> services;
    @FXML private ListView<ServicePriceData> prices;
    @FXML private TextField name, price;
    @FXML private TextField query;
    @FXML private TextArea description;
    @FXML private DatePicker date;
    @FXML private Label status;
    @FXML private Button editButton, deleteButton, saveButton;

    private final ContextMenu serviceContextMenu = new ContextMenu();
    private final MenuItem createMenuItem = new MenuItem("Создать");
    private final MenuItem editMenuItem = new MenuItem("Редактировать");
    private final MenuItem deleteMenuItem = new MenuItem("Удалить");
    private final ContextMenu priceContextMenu = new ContextMenu();
    private final MenuItem editPriceMenuItem = new MenuItem("Изменить цену");
    private final MenuItem deletePriceMenuItem = new MenuItem("Удалить цену");
    private FxContext context;
    private FormMode formMode = FormMode.VIEW;
    private int nextPage;
    private int loadGeneration;
    private int pricesGeneration;
    private long totalServices;
    private Long pricesServiceId;
    private BigDecimal editInitialPrice;
    private LocalDate editInitialPriceDate;
    private boolean hasNextPage;
    private boolean loadingPage;
    private boolean batchMode = true;
    private ScrollBar verticalScrollBar;
    private final ChangeListener<Number> scrollListener =
            (observable, oldValue, newValue) -> {
                if (verticalScrollBar != null
                        && newValue.doubleValue() >= verticalScrollBar.getMax() - 0.01) {
                    loadNextPage();
                }
            };

    @FXML
    private void initialize() {
        StatusMessages.install(status);
        date.setValue(LocalDate.now());
        services.setCellFactory(
                list ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(WorkshopServiceData value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(empty || value == null ? null : value.name() + " · " + value.price());
                            }
                        });
        prices.setCellFactory(
                list ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(ServicePriceData value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(
                                        empty || value == null
                                                ? null
                                                : value.validFrom()
                                                        + " → "
                                                        + (value.validTo() == null
                                                                ? "действует"
                                                                : value.validTo())
                                                        + " · "
                                                        + value.price());
                            }
                        });
        services.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable, oldValue, selected) -> {
                            if (selected == null) {
                                pricesServiceId = null;
                                prices.getItems().clear();
                                if (formMode == FormMode.VIEW) clearForm();
                            } else {
                                showService(selected);
                                loadPrices(selected.id());
                            }
                            updateActions();
                        });
        serviceContextMenu.getItems().setAll(createMenuItem, editMenuItem, deleteMenuItem);
        createMenuItem.setOnAction(event -> create());
        editMenuItem.setOnAction(event -> edit());
        deleteMenuItem.setOnAction(event -> delete());
        services.setContextMenu(serviceContextMenu);
        services.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::prepareContextMenu);
        priceContextMenu.getItems().setAll(editPriceMenuItem, deletePriceMenuItem);
        editPriceMenuItem.setOnAction(event -> editSelectedPrice());
        deletePriceMenuItem.setOnAction(event -> deleteSelectedPrice());
        prices.setContextMenu(priceContextMenu);
        prices.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::preparePriceContextMenu);
        services.setOnMouseClicked(
                event -> {
                    if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY
                            && event.getClickCount() == 2) edit();
                });
        services.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.ENTER) edit();
                });
        services.skinProperty().addListener((observable, oldSkin, newSkin) -> trackScrollBar());
        services.sceneProperty().addListener((observable, oldScene, newScene) -> trackScrollBar());
        trackScrollBar();
        updateActions();
    }

    public void bind(FxContext value) {
        context = value;
        updateActions();
    }

    public void refresh() {
        if (context != null && context.connection.connectedProperty().get()) search();
    }

    @FXML
    private void search() {
        if (context == null) return;
        String searchText = query.getText() == null ? "" : query.getText().strip();
        if (searchText.isEmpty()) {
            loadFirstPage();
            return;
        }
        batchMode = false;
        loadingPage = false;
        hasNextPage = false;
        int generation = ++loadGeneration;
        status.setText("Поиск услуг…");
        context.catalog.searchServices(
                searchText,
                result -> {
                    if (generation != loadGeneration) return;
                    services.getItems().setAll(result);
                    status.setText("Найдено услуг: " + result.size() + ".");
                },
                error -> {
                    if (generation == loadGeneration) error(error);
                });
    }

    private void loadFirstPage() {
        batchMode = true;
        formMode = FormMode.VIEW;
        nextPage = 0;
        totalServices = 0;
        hasNextPage = true;
        loadingPage = false;
        prices.getItems().clear();
        clearForm();
        services.getItems().clear();
        updateActions();
        loadPage(0, ++loadGeneration, false);
    }

    private void loadNextPage() {
        if (context == null || !batchMode || loadingPage || !hasNextPage) return;
        loadPage(nextPage, loadGeneration, true);
    }

    private void loadPage(int page, int generation, boolean append) {
        if (context == null) return;
        loadingPage = true;
        status.setText(append ? "Загружаю следующие услуги…" : "Загрузка услуг…");
        context.catalog.listServicesBatch(
                page,
                SERVICES_PAGE_SIZE,
                result -> {
                    if (generation != loadGeneration) return;
                    loadingPage = false;
                    if (append) services.getItems().addAll(result.services());
                    else services.getItems().setAll(result.services());
                    nextPage = result.page() + 1;
                    totalServices = result.totalElements();
                    hasNextPage = result.hasNext();
                    status.setText(batchStatus());
                },
                error -> {
                    if (generation != loadGeneration) return;
                    loadingPage = false;
                    error(error);
                });
    }

    private String batchStatus() {
        String loaded = "Загружено услуг: " + services.getItems().size() + " из " + totalServices + ".";
        return hasNextPage ? loaded + " Прокрутите список вниз, чтобы загрузить ещё." : loaded;
    }

    private void trackScrollBar() {
        Platform.runLater(
                () -> {
                    if (services.getScene() == null || services.getSkin() == null) return;
                    services.applyCss();
                    ScrollBar found =
                            services.lookupAll(".scroll-bar").stream()
                                    .filter(ScrollBar.class::isInstance)
                                    .map(ScrollBar.class::cast)
                                    .filter(bar -> bar.getOrientation() == Orientation.VERTICAL)
                                    .findFirst()
                                    .orElse(null);
                    if (verticalScrollBar == found) return;
                    if (verticalScrollBar != null) {
                        verticalScrollBar.valueProperty().removeListener(scrollListener);
                    }
                    verticalScrollBar = found;
                    if (verticalScrollBar != null) {
                        verticalScrollBar.valueProperty().addListener(scrollListener);
                    }
                });
    }

    @FXML
    private void create() {
        services.getSelectionModel().clearSelection();
        prices.getItems().clear();
        clearForm();
        date.setValue(LocalDate.now());
        formMode = FormMode.CREATE;
        updateActions();
    }

    @FXML
    private void edit() {
        WorkshopServiceData selected = services.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (selected.id().equals(pricesServiceId)) {
            beginEdit(selected);
            return;
        }
        status.setText("Загружаю историю цен…");
        loadPrices(
                selected.id(),
                () -> {
                    if (selected.equals(services.getSelectionModel().getSelectedItem())) beginEdit(selected);
                });
    }

    @FXML
    private void delete() {
        WorkshopServiceData selected = services.getSelectionModel().getSelectedItem();
        if (context == null || selected == null) return;
        Alert confirmation =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Удалить услугу «" + selected.name() + "»?",
                        ButtonType.CANCEL,
                        ButtonType.OK);
        confirmation.setTitle("Удаление услуги");
        confirmation.setHeaderText("Подтвердите удаление услуги и её цен");
        confirmation.initOwner(services.getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        long serviceId = selected.id();
        deleteButton.setDisable(true);
        status.setText("Удаление услуги…");
        context.catalog.deleteService(
                serviceId,
                () -> {
                    services.getItems().removeIf(service -> service.id().equals(serviceId));
                    totalServices = Math.max(0, totalServices - 1);
                    services.getSelectionModel().clearSelection();
                    prices.getItems().clear();
                    clearForm();
                    formMode = FormMode.VIEW;
                    status.setText(batchStatus());
                    updateActions();
                },
                error -> {
                    StatusMessages.error(status, error.getMessage());
                    updateActions();
                });
    }

    @FXML
    private void save() {
        if (context == null) return;
        if (formMode == FormMode.CREATE) createService();
        else if (formMode == FormMode.EDIT) updateService();
    }

    private void createService() {
        try {
            context.catalog.createService(
                    new CreateServiceRequest(
                            name.getText(),
                            description.getText(),
                            new BigDecimal(price.getText().replace(',', '.')),
                            date.getValue().toString()),
                    value -> {
                        formMode = FormMode.VIEW;
                        updateActions();
                        loadFirstPage();
                    },
                    this::error);
        } catch (Exception exception) {
            error(exception);
        }
    }

    private void updateService() {
        WorkshopServiceData selected = services.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            BigDecimal editedPrice = readPrice();
            boolean priceChanged = !samePrice(editInitialPrice, editedPrice);
            boolean startDateChanged = !java.util.Objects.equals(editInitialPriceDate, date.getValue());
            boolean savePrice = priceChanged || startDateChanged;
            if (savePrice && editedPrice == null) {
                throw new IllegalArgumentException("Укажите новую цену.");
            }
            if (savePrice && date.getValue() == null) {
                throw new IllegalArgumentException("Укажите дату начала действия цены.");
            }
            if (savePrice && !prices.getItems().isEmpty()
                    && !date.getValue().isAfter(LocalDate.parse(prices.getItems().getFirst().validFrom()))) {
                throw new IllegalArgumentException("Новая цена должна начинать действовать после последней записи в истории.");
            }
            context.catalog.updateService(
                selected.id(),
                new UpdateServiceRequest(name.getText(), description.getText()),
                updated -> {
                    if (savePrice) {
                        context.catalog.createPrice(
                                selected.id(),
                                new CreatePriceRequest(editedPrice, date.getValue().toString()),
                                createdPrice -> {
                                    loadPrices(selected.id());
                                    context.catalog.findService(
                                            selected.id(), this::finishEdit, this::error);
                                },
                                this::error);
                    } else {
                        finishEdit(updated);
                    }
                },
                this::error);
        } catch (Exception exception) {
            error(exception);
        }
    }

    private void beginEdit(WorkshopServiceData selected) {
        showService(selected);
        formMode = FormMode.EDIT;
        editInitialPrice = selected.price();
        editInitialPriceDate = nextPriceStartDate();
        date.setValue(editInitialPriceDate);
        updateActions();
    }

    private LocalDate nextPriceStartDate() {
        LocalDate next = LocalDate.now();
        if (!prices.getItems().isEmpty()) {
            LocalDate afterLatest = LocalDate.parse(prices.getItems().getFirst().validFrom()).plusDays(1);
            if (afterLatest.isAfter(next)) next = afterLatest;
        }
        return next;
    }

    private void finishEdit(WorkshopServiceData updated) {
        replaceService(updated);
        formMode = FormMode.VIEW;
        showService(updated);
        status.setText("Услуга обновлена.");
        updateActions();
    }

    private BigDecimal readPrice() {
        String value = price.getText() == null ? "" : price.getText().strip();
        return value.isEmpty() ? null : new BigDecimal(value.replace(',', '.'));
    }

    private boolean samePrice(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    private void loadPrices(Long serviceId) {
        loadPrices(serviceId, () -> { });
    }

    private void loadPrices(Long serviceId, Runnable success) {
        if (context == null) return;
        int generation = ++pricesGeneration;
        pricesServiceId = null;
        context.catalog.listPrices(
                serviceId,
                values -> {
                    if (generation != pricesGeneration) return;
                    prices.getItems().setAll(values);
                    pricesServiceId = serviceId;
                    success.run();
                },
                error -> {
                    if (generation == pricesGeneration) error(error);
                });
    }

    private void preparePriceContextMenu(ContextMenuEvent event) {
        Node target = event.getPickResult().getIntersectedNode();
        while (target != null && !(target instanceof ListCell<?>)) target = target.getParent();
        if (target instanceof ListCell<?> cell) {
            if (cell.isEmpty()) prices.getSelectionModel().clearSelection();
            else prices.getSelectionModel().select(cell.getIndex());
        }
        boolean canManagePrice =
                formMode == FormMode.VIEW
                        && services.getSelectionModel().getSelectedItem() != null
                        && prices.getSelectionModel().getSelectedItem() != null;
        editPriceMenuItem.setDisable(!canManagePrice);
        deletePriceMenuItem.setDisable(!canManagePrice);
    }

    private void editSelectedPrice() {
        WorkshopServiceData service = services.getSelectionModel().getSelectedItem();
        ServicePriceData selectedPrice = prices.getSelectionModel().getSelectedItem();
        if (context == null || service == null || selectedPrice == null || formMode != FormMode.VIEW) return;

        TextInputDialog dialog = new TextInputDialog(selectedPrice.price().toPlainString());
        dialog.setTitle("Изменение цены");
        dialog.setHeaderText("Изменить сумму цены");
        dialog.setContentText("Новая цена:");
        dialog.initOwner(prices.getScene().getWindow());
        dialog.showAndWait().ifPresent(
                input -> {
                    try {
                        BigDecimal amount = new BigDecimal(input.strip().replace(',', '.'));
                        context.catalog.updatePrice(
                                service.id(),
                                selectedPrice.id(),
                                new UpdatePriceRequest(amount),
                                updated -> {
                                    status.setText("Цена изменена.");
                                    reloadPriceHistory(service.id());
                                },
                                this::error);
                    } catch (NumberFormatException exception) {
                        error(new IllegalArgumentException("Введите корректную сумму цены.", exception));
                    }
                });
    }

    private void deleteSelectedPrice() {
        WorkshopServiceData service = services.getSelectionModel().getSelectedItem();
        ServicePriceData selectedPrice = prices.getSelectionModel().getSelectedItem();
        if (context == null || service == null || selectedPrice == null || formMode != FormMode.VIEW) return;

        Alert confirmation =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Удалить цену " + selectedPrice.price() + " от " + selectedPrice.validFrom() + "?",
                        ButtonType.CANCEL,
                        ButtonType.OK);
        confirmation.setTitle("Удаление цены");
        confirmation.setHeaderText("Подтвердите удаление записи из истории цен");
        confirmation.initOwner(prices.getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        deletePriceMenuItem.setDisable(true);
        context.catalog.deletePrice(
                service.id(),
                selectedPrice.id(),
                () -> {
                    status.setText("Цена удалена.");
                    reloadPriceHistory(service.id());
                },
                this::error);
    }

    private void reloadPriceHistory(Long serviceId) {
        loadPrices(serviceId);
        context.catalog.findService(
                serviceId,
                updated -> {
                    replaceService(updated);
                    WorkshopServiceData selected = services.getSelectionModel().getSelectedItem();
                    if (selected != null && selected.id().equals(serviceId)) showService(updated);
                },
                this::error);
    }

    private void replaceService(WorkshopServiceData updated) {
        for (int index = 0; index < services.getItems().size(); index++) {
            if (services.getItems().get(index).id().equals(updated.id())) {
                services.getItems().set(index, updated);
                return;
            }
        }
    }

    private void showService(WorkshopServiceData service) {
        name.setText(service.name());
        description.setText(service.description());
        price.setText(service.price() == null ? "" : service.price().toPlainString());
        formMode = FormMode.VIEW;
        updateActions();
    }

    private void clearForm() {
        name.clear();
        description.clear();
        price.clear();
    }

    private void updateActions() {
        boolean selected = services.getSelectionModel().getSelectedItem() != null;
        boolean formOpen = formMode != FormMode.VIEW;
        if (name == null) return;
        name.setDisable(formMode == FormMode.VIEW);
        description.setDisable(formMode == FormMode.VIEW);
        boolean priceEditable = formMode == FormMode.CREATE || formMode == FormMode.EDIT;
        price.setDisable(!priceEditable);
        date.setDisable(!priceEditable);
        saveButton.setVisible(formOpen);
        saveButton.setManaged(formOpen);
        saveButton.setText("Сохранить");
        editButton.setDisable(!selected || formOpen);
        deleteButton.setDisable(!selected || formOpen);
        editMenuItem.setDisable(!selected || formOpen);
        deleteMenuItem.setDisable(!selected || formOpen);
    }

    private void prepareContextMenu(ContextMenuEvent event) {
        Node target = event.getPickResult().getIntersectedNode();
        while (target != null && !(target instanceof ListCell<?>)) target = target.getParent();
        if (target instanceof ListCell<?> cell) {
            if (cell.isEmpty()) services.getSelectionModel().clearSelection();
            else services.getSelectionModel().select(cell.getIndex());
        }
        updateActions();
    }

    private void error(Exception exception) {
        StatusMessages.error(status, exception.getMessage());
    }
}
