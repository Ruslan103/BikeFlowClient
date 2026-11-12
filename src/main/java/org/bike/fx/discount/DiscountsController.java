package org.bike.fx.discount;

import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;

import org.bike.discount.api.dto.*;
import org.bike.fx.*;

import java.math.BigDecimal;

public final class DiscountsController implements SectionController {
    private static final int DISCOUNTS_PAGE_SIZE = 50;

    private enum FormMode { VIEW, CREATE, EDIT }

    @FXML private ListView<DiscountData> discounts;
    @FXML private TextField query;
    @FXML private TextField name, percentage;
    @FXML private Label status;
    @FXML private Button editButton, deleteButton, saveButton;

    private final ContextMenu discountContextMenu = new ContextMenu();
    private final MenuItem createMenuItem = new MenuItem("Создать");
    private final MenuItem editMenuItem = new MenuItem("Редактировать");
    private final MenuItem deleteMenuItem = new MenuItem("Удалить");
    private FxContext context;
    private FormMode formMode = FormMode.VIEW;
    private int nextPage;
    private int loadGeneration;
    private long totalDiscounts;
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
        discounts.setCellFactory(
                list ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(DiscountData value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(
                                        empty || value == null
                                                ? null
                                                : value.name() + " · " + value.percentage() + " %");
                            }
                        });
        discounts.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable, oldValue, selected) -> {
                            formMode = FormMode.VIEW;
                            if (selected == null) clearForm();
                            else showDiscount(selected);
                            updateActions();
                        });
        discountContextMenu.getItems().setAll(createMenuItem, editMenuItem, deleteMenuItem);
        createMenuItem.setOnAction(event -> create());
        editMenuItem.setOnAction(event -> edit());
        deleteMenuItem.setOnAction(event -> delete());
        discounts.setContextMenu(discountContextMenu);
        discounts.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::prepareContextMenu);
        discounts.setOnMouseClicked(
                event -> {
                    if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY
                            && event.getClickCount() == 2) edit();
                });
        discounts.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.ENTER) edit();
                });
        discounts.skinProperty().addListener((observable, oldSkin, newSkin) -> trackScrollBar());
        discounts.sceneProperty().addListener((observable, oldScene, newScene) -> trackScrollBar());
        trackScrollBar();
        updateActions();
    }

    public void bind(FxContext value) {
        context = value;
        updateActions();
    }

    public void refresh() {
        if (context != null && context.connection.connectedProperty().get())
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
        hasNextPage = false;
        int generation = ++loadGeneration;
        status.setText("Поиск скидок…");
        context.discounts.search(
                searchText,
                result -> {
                    if (generation != loadGeneration) return;
                    discounts.getItems().setAll(result);
                    status.setText("Найдено скидок: " + result.size() + ".");
                },
                error -> {
                    if (generation == loadGeneration) error(error);
                });
    }

    private void loadFirstPage() {
        batchMode = true;
        formMode = FormMode.VIEW;
        nextPage = 0;
        totalDiscounts = 0;
        hasNextPage = true;
        loadingPage = false;
        discounts.getItems().clear();
        clearForm();
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
        status.setText(append ? "Загружаю следующие скидки…" : "Загрузка скидок…");
        context.discounts.listBatch(
                page,
                DISCOUNTS_PAGE_SIZE,
                result -> {
                    if (generation != loadGeneration) return;
                    loadingPage = false;
                    if (append) discounts.getItems().addAll(result.discounts());
                    else discounts.getItems().setAll(result.discounts());
                    nextPage = result.page() + 1;
                    totalDiscounts = result.totalElements();
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
        String loaded = "Загружено скидок: " + discounts.getItems().size() + " из " + totalDiscounts + ".";
        return hasNextPage ? loaded + " Прокрутите список вниз, чтобы загрузить ещё." : loaded;
    }

    private void trackScrollBar() {
        Platform.runLater(
                () -> {
                    if (discounts.getScene() == null || discounts.getSkin() == null) return;
                    discounts.applyCss();
                    ScrollBar found =
                            discounts.lookupAll(".scroll-bar").stream()
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
        discounts.getSelectionModel().clearSelection();
        clearForm();
        formMode = FormMode.CREATE;
        updateActions();
    }

    @FXML
    private void edit() {
        DiscountData selected = discounts.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        showDiscount(selected);
        formMode = FormMode.EDIT;
        updateActions();
    }

    @FXML
    private void save() {
        if (context == null || formMode == FormMode.VIEW) return;
        try {
            var request = new DiscountRequest(name.getText(), new BigDecimal(percentage.getText().replace(',', '.')));
            if (formMode == FormMode.CREATE) {
                context.discounts.create(
                        request,
                        value -> {
                            formMode = FormMode.VIEW;
                            clearForm();
                            updateActions();
                            status.setText("Скидка создана.");
                            refresh();
                        },
                        this::error);
            } else {
                DiscountData selected = discounts.getSelectionModel().getSelectedItem();
                if (selected == null) return;
                context.discounts.update(
                        selected.id(),
                        request,
                        updated -> {
                            replaceDiscount(updated);
                            showDiscount(updated);
                            formMode = FormMode.VIEW;
                            updateActions();
                            status.setText("Скидка обновлена.");
                        },
                        this::error);
            }
        } catch (Exception error) {
            error(error);
        }
    }

    @FXML
    private void delete() {
        DiscountData selected = discounts.getSelectionModel().getSelectedItem();
        if (context == null || selected == null || formMode != FormMode.VIEW) return;
        var confirmation =
                FxView.confirmation(
                        "Удалить скидку «" + selected.name() + "»?",
                        ButtonType.YES,
                        ButtonType.NO);
        confirmation.initOwner(discounts.getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        deleteButton.setDisable(true);
        context.discounts.delete(
                selected.id(),
                () -> {
                    discounts.getItems().removeIf(discount -> discount.id().equals(selected.id()));
                    totalDiscounts = Math.max(0, totalDiscounts - 1);
                    discounts.getSelectionModel().clearSelection();
                    clearForm();
                    status.setText("Скидка удалена. " + batchStatus());
                    updateActions();
                },
                this::error);
    }

    private void showDiscount(DiscountData discount) {
        name.setText(discount.name());
        percentage.setText(discount.percentage().toPlainString());
        formMode = FormMode.VIEW;
        updateActions();
    }

    private void replaceDiscount(DiscountData updated) {
        for (int index = 0; index < discounts.getItems().size(); index++) {
            if (discounts.getItems().get(index).id().equals(updated.id())) {
                discounts.getItems().set(index, updated);
                return;
            }
        }
    }

    private void clearForm() {
        name.clear();
        percentage.clear();
    }

    private void updateActions() {
        if (name == null) return;
        boolean selected = discounts.getSelectionModel().getSelectedItem() != null;
        boolean formOpen = formMode != FormMode.VIEW;
        name.setDisable(!formOpen);
        percentage.setDisable(!formOpen);
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
            if (cell.isEmpty()) discounts.getSelectionModel().clearSelection();
            else discounts.getSelectionModel().select(cell.getIndex());
        }
        updateActions();
    }

    private void error(Exception error) {
        StatusMessages.error(status, error.getMessage());
        updateActions();
    }
}
