package org.bike.fx.workorder;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.util.Duration;

import org.bike.trello.api.TrelloApi;
import org.bike.trello.model.TrelloData.Board;
import org.bike.trello.model.TrelloData.BoardList;
import org.bike.trello.model.TrelloData.Label;
import org.bike.trello.model.TrelloData.Selection;
import org.bike.fx.SearchableComboBox;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Keeps live Trello choices separate from the work-order draft and its client snapshot. */
public final class TrelloFields implements AutoCloseable {
    public record View(ComboBox<Board> board, ComboBox<BoardList> list, ComboBox<Label> labels) {}

    private final javafx.collections.ObservableSet<String> selectedLabelIds = javafx.collections.FXCollections.observableSet();
    private final View view;
    private final TrelloApi api;
    private final Consumer<Exception> failure;
    private final SearchableComboBox<Board> boardOptions;
    private final SearchableComboBox<BoardList> listOptions;
    private final SearchableComboBox<Label> labelOptions;
    private final Timeline refreshTimer;
    private String orderNumber, cardId, clean = "";
    private long revision;
    private boolean updating, loading;
    private boolean selectFirstBoard = true;
    private static final Board NO_BOARD = new Board(null, "");

    public TrelloFields(View view, TrelloApi api, Consumer<Exception> failure) {
        this.view = view;
        this.api = api;
        this.failure = failure;
        labelOptions = SearchableComboBox.install(view.labels(), this::labelText);
        labelOptions.keepPopupOpenOnCommit(true);
        MenuItem clearLabels = new MenuItem("Снять все метки");
        clearLabels.setOnAction(event -> {
            selectedLabelIds.clear();
            refreshLabelSummary();
            if (view.labels().getSkin() instanceof javafx.scene.control.skin.ComboBoxListViewSkin<?> skin
                    && skin.getPopupContent() instanceof javafx.scene.control.ListView<?> list) list.refresh();
        });
        ContextMenu labelsContextMenu = new ContextMenu(clearLabels);
        view.labels().setContextMenu(labelsContextMenu);
        view.labels().getEditor().setContextMenu(labelsContextMenu);
        view.labels().skinProperty().addListener((observable, previous, skin) -> {
            if (skin instanceof javafx.scene.control.skin.ComboBoxListViewSkin<?> comboSkin) {
                comboSkin.setHideOnClick(false);
            }
        });
        if (view.labels().getSkin() instanceof javafx.scene.control.skin.ComboBoxListViewSkin<?> comboSkin) {
            comboSkin.setHideOnClick(false);
        }
        labelOptions.onCommit(() -> {
            Label label = view.labels().getValue();
            if (label != null && !selectedLabelIds.remove(label.id())) selectedLabelIds.add(label.id());
            refreshLabelSummary();
            if (view.labels().getSkin() instanceof javafx.scene.control.skin.ComboBoxListViewSkin<?> skin
                    && skin.getPopupContent() instanceof javafx.scene.control.ListView<?> list) list.refresh();
        });
        view.labels().setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            private final javafx.scene.control.CheckBox check = new javafx.scene.control.CheckBox();
            {
                check.setOnAction(event -> {
                    Label label = getItem();
                    if (label == null) return;
                    if (check.isSelected()) selectedLabelIds.add(label.id()); else selectedLabelIds.remove(label.id());
                    refreshLabelSummary();
                });
            }
            @Override protected void updateItem(Label label, boolean empty) {
                super.updateItem(label, empty);
                setText(null);
                if (empty || label == null) setGraphic(null);
                else { check.setText(labelText(label)); check.setSelected(selectedLabelIds.contains(label.id())); setGraphic(check); }
            }
        });
        view.labels().setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(Label label, boolean empty) { super.updateItem(label, empty); setText(labelSummary()); }
        });
        view.labels().setOnShowing(event -> {
            refreshLabelSummary();
            labelOptions.resetSearch();
        });
        view.labels().setOnHidden(event -> javafx.application.Platform.runLater(() -> {
            if (!view.labels().isShowing()) refreshLabelSummary();
        }));
        boardOptions = SearchableComboBox.install(view.board(), Board::name);
        listOptions = SearchableComboBox.install(view.list(), BoardList::name);
        Runnable boardSelected = () -> {
            if (!updating) loadBoard(++revision, null, false);
        };
        boardOptions.onSelection(boardSelected);
        boardOptions.onCommit(boardSelected);
        refreshTimer =
                new Timeline(
                        new KeyFrame(
                                Duration.seconds(30),
                                event -> {
                                    if (view.board().getScene() != null
                                            && view.board().getScene().getWindow() != null
                                            && view.board().getScene().getWindow().isShowing())
                                        refresh();
                                }));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
        reset();
    }

    public void reset() {
        revision++;
        selectFirstBoard = true;
        orderNumber = null;
        cardId = null;
        updating = true;
        view.board().setValue(null);
        listOptions.setItems(List.of());
        labelOptions.setItems(List.of());
        labelOptions.clear();
        selectedLabelIds.clear(); refreshLabelSummary();
        updating = false;
        setLoading(false);
        clean = key();
    }

    public void open(String number, String card) {
        reset();
        selectFirstBoard = false;
        orderNumber = number;
        cardId = card;
        refresh();
    }

    public boolean isDirty() {
        return !clean.equals(key());
    }

    public Selection selection() {
        if (loading) throw new IllegalStateException("Дождитесь загрузки данных Trello.");
        var board = view.board().getValue();
        var list = view.list().getValue();
        if (board != null && board.id() != null && list == null)
            throw new IllegalStateException("Выберите колонку Trello.");
        return new Selection(
                board == null ? null : board.id(),
                list == null ? null : list.id(),
                selectedLabels());
    }

    public void restore(Selection selection) {
        selectFirstBoard = false;
        loadBoards(++revision, selection, false);
    }

    public void refresh() {
        if (loading) return;
        long token = ++revision;
        boolean pristine = !isDirty();
        setLoading(true);
        if (cardId != null && orderNumber != null && pristine) {
            api.card(
                    orderNumber,
                    card -> {
                        if (token == revision)
                            loadBoards(
                                    token,
                                    new Selection(card.idBoard(), card.idList(), card.idLabels()),
                                    true);
                    },
                    error -> failed(token, error));
        } else loadBoards(token, rawSelection(), pristine);
    }

    private Selection rawSelection() {
        var board = view.board().getValue();
        var list = view.list().getValue();
        return new Selection(
                board == null ? null : board.id(),
                list == null ? null : list.id(),
                selectedLabels());
    }

    private void loadBoards(long token, Selection selection, boolean markClean) {
        setLoading(true);
        api.boards(
                values -> {
                    if (token != revision) return;
                    updating = true;
                    var options = new java.util.ArrayList<Board>();
                    options.add(NO_BOARD);
                    options.addAll(values);
                    boardOptions.setItems(options);
                    Board fallback = selectFirstBoard && !values.isEmpty()
                            ? values.getFirst() : NO_BOARD;
                    selectFirstBoard = false;
                    view.board()
                            .setValue(
                                    values.stream()
                                            .filter(
                                                    value ->
                                                            Objects.equals(
                                                                    value.id(),
                                                                    selection.boardId()))
                                            .findFirst()
                                            .orElse(fallback));
                    updating = false;
                    loadBoard(token, selection, markClean);
                },
                error -> failed(token, error));
    }

    private void loadBoard(long token, Selection selection, boolean markClean) {
        if (selection == null) selection = rawSelection();
        var selectedBoard = view.board().getValue();
        if (selectedBoard == null || selectedBoard.id() == null) {
            listOptions.setItems(List.of());
            labelOptions.setItems(List.of());
        labelOptions.clear();
        selectedLabelIds.clear(); refreshLabelSummary();
            setLoading(false);
            if (markClean) clean = key();
            return;
        }
        setLoading(true);
        var wanted = selection;
        int[] pending = {2};
        Runnable done =
                () -> {
                    if (--pending[0] == 0) {
                        setLoading(false);
                        if (markClean) clean = key();
                    }
                };
        api.lists(
                selectedBoard.id(),
                values -> {
                    if (token != revision) return;
                    listOptions.setItems(values);
                    view.list()
                            .setValue(
                                    values.stream()
                                            .filter(
                                                    value ->
                                                            Objects.equals(
                                                                    value.id(), wanted.listId()))
                                            .findFirst()
                                            .orElse(null));
                    done.run();
                },
                error -> failed(token, error));
        api.labels(
                selectedBoard.id(),
                values -> {
                    if (token != revision) return;
                    labelOptions.setItems(values);
                    selectedLabelIds.clear();
                    values.stream().filter(label -> wanted.labelIds().contains(label.id())).forEach(label -> selectedLabelIds.add(label.id()));
                    refreshLabelSummary();
                    done.run();
                },
                error -> failed(token, error));
    }

    private List<String> selectedLabels() {
        return selectedLabelIds.stream().sorted().toList();
    }

    private String labelText(Label label) {
        return label.name() == null || label.name().isBlank() ? label.color() : label.name() + " · " + label.color();
    }

    private String labelSummary() {
        String text = labelOptions.items().stream().filter(label -> selectedLabelIds.contains(label.id()))
                .map(label -> label.name() == null || label.name().isBlank() ? label.color() : label.name())
                .collect(java.util.stream.Collectors.joining(", "));
        return text.isEmpty() ? "Выберите метки Trello" : text;
    }

    private void refreshLabelSummary() {
        String summary = labelSummary();
        if (!view.labels().isShowing()) labelOptions.setDisplayText(selectedLabelIds.isEmpty() ? "" : summary);
        view.labels().getButtonCell().setText(summary);
        view.labels().setPromptText(summary);
        view.labels().setTooltip(new javafx.scene.control.Tooltip(summary));
    }

    private String key() {
        return rawSelection().toString();
    }

    private void setLoading(boolean value) {
        loading = value;
        view.board().setDisable(value && boardOptions.items().isEmpty());
        view.list().setDisable(value);
        view.labels().setDisable(value);
    }

    private void failed(long token, Exception error) {
        if (token != revision) return;
        revision++;
        setLoading(false);
        failure.accept(error);
    }

    @Override
    public void close() {
        refreshTimer.stop();
        revision++;
    }
}

