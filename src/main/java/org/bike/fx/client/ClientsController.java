package org.bike.fx.client;

import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.geometry.Orientation;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.beans.value.ChangeListener;

import org.bike.client.api.dto.*;
import org.bike.client.presentation.ClientDisplay;
import org.bike.fx.*;

import java.util.function.Consumer;

public final class ClientsController implements SectionController {
    private static final int CLIENTS_PAGE_SIZE = 50;

    @FXML private ListView<ClientData> clients;
    @FXML private ListView<ClientWorkOrderData> workOrders;
    @FXML private TextField query;
    @FXML private TextArea details;
    @FXML private Label status;
    @FXML private Button chooseButton;
    @FXML private Button deleteButton;
    private final ContextMenu clientContextMenu = new ContextMenu();
    private final MenuItem createMenuItem = new MenuItem("Создать клиента");
    private final MenuItem openMenuItem = new MenuItem("Редактировать клиента");
    private final MenuItem refreshMenuItem = new MenuItem("Обновить");
    private final MenuItem chooseMenuItem = new MenuItem("Выбрать клиента");
    private final MenuItem deleteMenuItem = new MenuItem("Удалить клиента");
    private final ContextMenu workOrderContextMenu = new ContextMenu();
    private final MenuItem openWorkOrderMenuItem = new MenuItem("Открыть бланк");
    private FxContext context;
    private Consumer<ClientData> chosen;
    private Consumer<String> workOrderOpener;
    private int nextPage;
    private int loadGeneration;
    private long totalClients;
    private boolean hasNextPage;
    private boolean loadingPage;
    private boolean batchMode;
    private boolean deletingClient;
    private int workOrdersGeneration;
    private ScrollBar verticalScrollBar;
    private final ChangeListener<Number> scrollListener =
            (observable, oldValue, newValue) -> {
                if (newValue.doubleValue() >= verticalScrollBar.getMax() - 0.01) {
                    loadNextPage();
                }
            };

    @FXML
    private void initialize() {
        StatusMessages.install(status);
        chooseButton.setVisible(false);
        chooseButton.setManaged(false);
        clients.setCellFactory(
                list ->
                        new ListCell<>() {
                            protected void updateItem(ClientData value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(
                                        empty || value == null
                                                ? null
                                                : ClientDisplay.name(value)
                                                        + " · № "
                                                        + value.publicId());
                            }
                        });
        workOrders.setCellFactory(
                list ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(ClientWorkOrderData value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(
                                        empty || value == null
                                                ? null
                                                : "№ " + value.blankNumber()
                                                        + (value.reportedIssue() == null
                                                                || value.reportedIssue().isBlank()
                                                                ? ""
                                                                : " · " + value.reportedIssue()));
                            }
                        });
        clients.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, a, b) -> {
                            details.setText(b == null ? "" : ClientDisplay.details(b));
                            loadWorkOrders(b);
                            updateClientContextMenu();
                        });
        clientContextMenu
                .getItems()
                .setAll(createMenuItem, openMenuItem, refreshMenuItem, chooseMenuItem, deleteMenuItem);
        createMenuItem.setOnAction(event -> create());
        openMenuItem.setOnAction(event -> open());
        refreshMenuItem.setOnAction(event -> search());
        chooseMenuItem.setOnAction(event -> choose());
        deleteMenuItem.setOnAction(event -> deleteSelectedClient());
        deleteButton.setOnAction(event -> deleteSelectedClient());
        clients.setContextMenu(clientContextMenu);
        clients.addEventHandler(
                ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::prepareClientContextMenu);
        clients.setOnMouseClicked(
                event -> {
                    if (chosen != null
                            && event.getButton() == javafx.scene.input.MouseButton.PRIMARY
                            && event.getClickCount() == 2) choose();
                });
        workOrderContextMenu.getItems().setAll(openWorkOrderMenuItem);
        openWorkOrderMenuItem.setOnAction(event -> openSelectedWorkOrder());
        workOrders.setContextMenu(workOrderContextMenu);
        workOrders.addEventHandler(
                ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::prepareWorkOrderContextMenu);
        workOrders.setOnMouseClicked(
                event -> {
                    if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY
                            && event.getClickCount() == 2) openSelectedWorkOrder();
                });
        workOrders.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.ENTER) openSelectedWorkOrder();
                });
        clients.skinProperty().addListener((observable, oldSkin, newSkin) -> trackScrollBar());
        clients.sceneProperty().addListener((observable, oldScene, newScene) -> trackScrollBar());
        trackScrollBar();
    }

    public void bind(FxContext value) {
        context = value;
        updateClientContextMenu();
    }

    public void setWorkOrderOpener(Consumer<String> opener) {
        workOrderOpener = opener;
    }

    public void refresh() {
        if (context != null && context.connection.connectedProperty().get()) search();
    }

    public void picker(Consumer<ClientData> callback, String text) {
        chosen = callback;
        query.setText(text);
        chooseButton.setVisible(true);
        chooseButton.setManaged(true);
        updateClientContextMenu();
        search();
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
        int generation = ++loadGeneration;
        status.setText("Поиск…");
        context.clients.search(
                searchText,
                result -> {
                    if (generation != loadGeneration) return;
                    clients.getItems().setAll(result.all());
                    status.setText(
                            "Совпадений: "
                                    + result.exact().size()
                                    + "; похожих контактов: "
                                    + result.similar().size()
                                    + ". Выберите клиента.");
                },
                error -> {
                    if (generation == loadGeneration) error(error);
                });
    }

    private void loadFirstPage() {
        batchMode = true;
        loadingPage = false;
        nextPage = 0;
        totalClients = 0;
        hasNextPage = true;
        clients.getItems().clear();
        loadPage(0, ++loadGeneration, false);
    }

    private void loadWorkOrders(ClientData client) {
        int generation = ++workOrdersGeneration;
        workOrders.getItems().clear();
        if (client == null || context == null) return;
        status.setText("Загрузка бланков клиента…");
        context.clients.workOrders(
                client.id(),
                result -> {
                    if (generation != workOrdersGeneration) return;
                    workOrders.getItems().setAll(result);
                    status.setText(result.isEmpty()
                            ? "У клиента пока нет бланков."
                            : batchMode ? batchStatus() : "Бланков клиента: " + result.size() + ".");
                },
                error -> {
                    if (generation == workOrdersGeneration) StatusMessages.error(status, error.getMessage());
                });
    }

    private void openSelectedWorkOrder() {
        ClientWorkOrderData selected = workOrders.getSelectionModel().getSelectedItem();
        if (selected != null && workOrderOpener != null) workOrderOpener.accept(selected.blankNumber());
    }

    private void prepareWorkOrderContextMenu(ContextMenuEvent event) {
        Node target = event.getPickResult().getIntersectedNode();
        while (target != null && !(target instanceof ListCell<?>)) target = target.getParent();
        if (target instanceof ListCell<?> cell) {
            if (cell.isEmpty()) workOrders.getSelectionModel().clearSelection();
            else workOrders.getSelectionModel().select(cell.getIndex());
        }
        openWorkOrderMenuItem.setDisable(
                workOrders.getSelectionModel().getSelectedItem() == null || workOrderOpener == null);
    }

    private void loadNextPage() {
        if (!batchMode || loadingPage || !hasNextPage || context == null) return;
        loadPage(nextPage, loadGeneration, true);
    }

    private void loadPage(int page, int generation, boolean append) {
        if (context == null) return;
        loadingPage = true;
        status.setText(append ? "Загружаю следующих клиентов…" : "Загрузка клиентов…");
        context.clients.batch(
                page,
                CLIENTS_PAGE_SIZE,
                result -> {
                    if (generation != loadGeneration) return;
                    loadingPage = false;
                    if (append) clients.getItems().addAll(result.clients());
                    else clients.getItems().setAll(result.clients());
                    nextPage = result.page() + 1;
                    totalClients = result.totalElements();
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
        String loaded = "Загружено клиентов: " + clients.getItems().size() + " из " + totalClients + ".";
        return hasNextPage ? loaded + " Прокрутите вниз, чтобы загрузить ещё." : loaded;
    }

    private void trackScrollBar() {
        Platform.runLater(
                () -> {
                    if (clients.getScene() == null || clients.getSkin() == null) return;
                    clients.applyCss();
                    ScrollBar found =
                            clients.lookupAll(".scroll-bar").stream()
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
    private void choose() {
        var selected = clients.getSelectionModel().getSelectedItem();
        if (selected != null && chosen != null) chosen.accept(selected);
    }

    private void updateClientContextMenu() {
        boolean hasSelection = clients.getSelectionModel().getSelectedItem() != null;
        createMenuItem.setDisable(context == null);
        openMenuItem.setDisable(context == null || !hasSelection);
        refreshMenuItem.setDisable(context == null);
        chooseMenuItem.setVisible(chosen != null);
        chooseMenuItem.setDisable(chosen == null || !hasSelection);
        deleteButton.setDisable(context == null || !hasSelection || deletingClient);
        deleteMenuItem.setDisable(context == null || !hasSelection || deletingClient);
    }

    private void prepareClientContextMenu(ContextMenuEvent event) {
        Node target = event.getPickResult().getIntersectedNode();
        while (target != null && !(target instanceof ListCell<?>)) target = target.getParent();
        if (target instanceof ListCell<?> cell) {
            if (cell.isEmpty()) clients.getSelectionModel().clearSelection();
            else clients.getSelectionModel().select(cell.getIndex());
        }
        updateClientContextMenu();
    }

    @FXML
    private void create() {
        edit(null);
    }

    @FXML
    private void open() {
        var selected = clients.getSelectionModel().getSelectedItem();
        if (selected != null) context.clients.find(selected.id(), this::edit, this::error);
    }

    @FXML
    private void deleteSelectedClient() {
        ClientData selected = clients.getSelectionModel().getSelectedItem();
        if (selected == null || context == null || deletingClient) return;

        Alert confirmation =
                new Alert(
                        AlertType.CONFIRMATION,
                        "Удалить клиента «" + ClientDisplay.name(selected) + "»?",
                        ButtonType.CANCEL,
                        ButtonType.OK);
        confirmation.setTitle("Удаление клиента");
        confirmation.setHeaderText("Подтвердите удаление карточки клиента");
        confirmation.initOwner(clients.getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        deletingClient = true;
        updateClientContextMenu();
        status.setText("Удаление клиента…");
        long clientId = selected.id();
        context.clients.delete(
                clientId,
                () -> {
                    deletingClient = false;
                    clients.getItems().removeIf(client -> client.id().equals(clientId));
                    totalClients = Math.max(0, totalClients - 1);
                    details.clear();
                    status.setText(batchMode ? batchStatus() : "Клиент удалён.");
                    updateClientContextMenu();
                },
                error -> {
                    deletingClient = false;
                    StatusMessages.error(status, error.getMessage());
                    updateClientContextMenu();
                });
    }

    private void edit(ClientData value) {
        FxView.Loaded<ClientEditorController> view = FxView.load("ClientEditor");
        var stage = FxView.dialog(clients.getScene().getWindow(), "Карточка клиента", view.root());
        view.controller()
                .bind(
                        context,
                        value,
                        saved -> {
                            search();
                            if (chosen != null) chosen.accept(saved);
                        });
        stage.setOnCloseRequest(
                event -> {
                    if (!view.controller().canLeave()) event.consume();
                });
        stage.showAndWait();
    }

    private void error(Exception error) {
        StatusMessages.error(status, error.getMessage());
    }
}
