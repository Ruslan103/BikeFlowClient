package org.bike.fx;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.StringConverter;

/** Adds text search to a combo box while keeping its full option list available. */
public final class SearchableComboBox<T> {
    private final ComboBox<T> combo;
    private final Function<T, String> text;
    private final ObservableList<T> allItems = FXCollections.observableArrayList();
    private final ObservableList<T> visibleItems = FXCollections.observableArrayList();
    private boolean updatingItems;
    private boolean updatingEditor;
    private boolean searching;
    private boolean committing;
    private boolean keepPopupOpenOnCommit;
    private Runnable onCommit;
    private Runnable onSelection;

    private SearchableComboBox(ComboBox<T> combo, Function<T, String> text) {
        this.combo = combo;
        this.text = text;
        var initialItems = FXCollections.observableArrayList(combo.getItems());
        combo.setEditable(true);
        combo.setItems(visibleItems);
        combo.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(T value) {
                        return value == null ? "" : SearchableComboBox.this.text.apply(value);
                    }

                    @Override
                    public T fromString(String value) {
                        return find(value);
                    }
                });
        combo.getEditor()
                .textProperty()
                .addListener(
                        (observable, previous, value) -> {
                                    if (updatingEditor || updatingItems) return;
                                    T selected = combo.getValue();
                                    if (combo.isShowing()
                                            && selected != null
                                            && normalize(value).equals(normalize(text.apply(selected)))) {
                                        searching = false;
                                        return;
                                    }
                            searching = true;
                            filter(value);
                            if (!combo.isShowing() && !visibleItems.isEmpty()) combo.show();
                        });
        combo.addEventFilter(
                        KeyEvent.KEY_PRESSED,
                        event -> {
                            if (event.getCode() != KeyCode.ENTER) return;
                            T value = findEditorValue(combo.getEditor().getText());
                            if (value == null) {
                                restoreEditorValue();
                            } else {
                                commit(value);
                                if (onCommit != null) onCommit.run();
                            }
                            event.consume();
                        });
        combo.addEventHandler(
                ActionEvent.ACTION,
                event -> {
                    if (!committing && !updatingItems && combo.getValue() != null) {
                        searching = false;
                        if (onSelection != null) onSelection.run();
                    }
                });
        combo.addEventHandler(
                ComboBoxBase.ON_SHOWING,
                event -> {
                    if (searching) filter(combo.getEditor().getText());
                    else showAllItems();
                });
        combo.addEventHandler(
                ComboBoxBase.ON_HIDDEN,
                event -> {
                    if (searching) {
                        searching = false;
                        T selected = combo.getValue();
                        Platform.runLater(() -> restoreItemsAndEditor(selected));
                    }
                });
        combo.valueProperty()
                .addListener(
                        (observable, previous, value) -> {
                            if (!searching && !committing) setEditorText(value);
                        });
        setItems(initialItems);
        restoreEditorValue();
    }

    public static <T> SearchableComboBox<T> install(
            ComboBox<T> combo, Function<T, String> text) {
        return new SearchableComboBox<>(combo, text);
    }

    public ObservableList<T> items() {
        return FXCollections.unmodifiableObservableList(allItems);
    }

    public void setItems(Collection<? extends T> values) {
        String query = searching ? combo.getEditor().getText() : "";
        T selected = combo.getValue();
        allItems.setAll(values);
        if (selected != null && !allItems.contains(selected)) combo.setValue(null);
        if (searching) filter(query);
        else showAllItems();
    }

    public void keepPopupOpenOnCommit(boolean value) {
        keepPopupOpenOnCommit = value;
    }

    public void onCommit(Runnable action) {
        onCommit = action;
    }

    public void setDisplayText(String value) {
        updatingEditor = true;
        searching = false;
        try { combo.getEditor().setText(value); }
        finally { updatingEditor = false; }
    }

    public void resetSearch() {
        committing = true;
        updatingEditor = true;
        searching = false;
        try {
            combo.setValue(null);
            showAllItems();
            combo.getEditor().clear();
        } finally {
            updatingEditor = false;
            committing = false;
        }
    }

    public void clear() {
        committing = true;
        searching = false;
        try {
            combo.setValue(null);
            combo.hide();
            showAllItems();
            restoreEditorValue();
        } finally {
            committing = false;
        }
    }

    public void onSelection(Runnable action) {
        onSelection = action;
    }

    private T find(String value) {
        if (value == null || value.isBlank()) return null;
        String query = normalize(value);
        return allItems.stream()
                .filter(item -> normalize(text.apply(item)).equals(query))
                .findFirst()
                .orElseGet(
                        () ->
                                allItems.stream()
                                        .filter(item -> normalize(text.apply(item)).startsWith(query))
                                        .findFirst()
                                        .orElse(null));
    }

    private T findEditorValue(String value) {
        T exact = find(value);
        if (exact != null) return exact;
        if (visibleItems.isEmpty()) return null;
        String query = normalize(value);
        return visibleItems.stream()
                .filter(item -> normalize(text.apply(item)).contains(query))
                .findFirst()
                .orElse(visibleItems.getFirst());
    }

    private void filter(String query) {
        if (updatingItems) return;
        String normalized = normalize(query);
        T selected = combo.getValue();
        updatingItems = true;
        try {
            visibleItems.setAll(
                    allItems.stream()
                            .filter(item -> normalize(text.apply(item)).contains(normalized))
                            .toList());
            if (selected != null && !Objects.equals(combo.getValue(), selected))
                combo.setValue(selected);
        } finally {
            updatingItems = false;
        }
    }

    private void showAllItems() {
        T selected = combo.getValue();
        updatingItems = true;
        try {
            visibleItems.setAll(allItems);
            if (selected != null && allItems.contains(selected)) combo.setValue(selected);
        } finally {
            updatingItems = false;
        }
    }

    private void commit(T value) {
        committing = true;
        combo.setValue(value);
        searching = false;
        if (keepPopupOpenOnCommit) {
            committing = false;
            return;
        }
        combo.hide();
        // JavaFX is still dispatching the popup selection at this point.
        // Replacing its items synchronously corrupts the ListView selection model.
        Platform.runLater(() -> restoreItemsAndEditor(value));
        committing = false;
    }

    private void restoreItemsAndEditor(T selected) {
        committing = true;
        try {
            showAllItems();
            if (selected != null && allItems.contains(selected)) combo.setValue(selected);
            restoreEditorValue();
        } finally {
            committing = false;
        }
    }

    private void restoreEditorValue() {
        setEditorText(combo.getValue());
    }

    private void setEditorText(T value) {
        updatingEditor = true;
        combo.getEditor().setText(value == null ? "" : text.apply(value));
        updatingEditor = false;
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").strip().toLowerCase(Locale.ROOT);
    }
}
