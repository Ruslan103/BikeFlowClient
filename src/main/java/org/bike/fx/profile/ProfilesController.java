package org.bike.fx.profile;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;

import org.bike.fx.*;
import org.bike.profile.ServerProfile;

import java.util.Arrays;
import java.util.Set;

public final class ProfilesController implements SectionController {
    private enum FormMode { VIEW, CREATE, EDIT }

    @FXML private ListView<ServerProfile> profiles;
    @FXML private TextField name, login;
    @FXML private PasswordField password;
    @FXML private CheckBox remember, defaultProfile;
    @FXML private Label status;
    @FXML private Button editButton, deleteButton, saveButton;

    private final ContextMenu profileContextMenu = new ContextMenu();
    private final MenuItem createMenuItem = new MenuItem("Создать");
    private final MenuItem editMenuItem = new MenuItem("Редактировать");
    private final MenuItem deleteMenuItem = new MenuItem("Удалить");
    private FxContext context;
    private ServerProfile selected;
    private FormMode formMode = FormMode.VIEW;

    @FXML
    private void initialize() {
        StatusMessages.install(status);
        profiles.setCellFactory(
                list ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(ServerProfile profile, boolean empty) {
                                super.updateItem(profile, empty);
                                setText(empty || profile == null
                                        ? null
                                        : profile.name() + " · " + profile.login());
                            }
                        });
        profiles.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, value) -> {
                    formMode = FormMode.VIEW;
                    display(value);
                    updateActions();
                });
        profileContextMenu.getItems().setAll(createMenuItem, editMenuItem, deleteMenuItem);
        createMenuItem.setOnAction(event -> create());
        editMenuItem.setOnAction(event -> edit());
        deleteMenuItem.setOnAction(event -> delete());
        profiles.setContextMenu(profileContextMenu);
        profiles.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::prepareContextMenu);
        profiles.setOnMouseClicked(
                event -> {
                    if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY
                            && event.getClickCount() == 2) edit();
                });
        profiles.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) edit();
        });
        updateActions();
    }

    @Override
    public void bind(FxContext value) {
        context = value;
        refresh();
        updateActions();
    }

    @Override
    public void refresh() {
        if (context != null) profiles.getItems().setAll(context.profiles.profiles());
    }

    private void display(ServerProfile value) {
        selected = value;
        name.setText(value == null ? "" : value.name());
        login.setText(value == null ? "" : value.login());
        password.clear();
        remember.setSelected(value != null && value.rememberPassword());
        defaultProfile.setSelected(value != null && value.defaultProfile());
    }

    @FXML
    private void create() {
        profiles.getSelectionModel().clearSelection();
        display(null);
        formMode = FormMode.CREATE;
        updateActions();
    }

    @FXML
    private void edit() {
        ServerProfile value = profiles.getSelectionModel().getSelectedItem();
        if (value == null) return;
        display(value);
        formMode = FormMode.EDIT;
        updateActions();
    }

    @FXML
    private void save() {
        if (context == null || formMode == FormMode.VIEW) return;
        String serverUrl = context.settings.serverUrl();
        if (serverUrl.isBlank()) {
            StatusMessages.error(status, "Сначала укажите адрес в Настройки → Настройки сервера.");
            return;
        }
        char[] secret = password.getText().toCharArray();
        try {
            var value =
                    context.profiles.save(
                            new ServerProfile(
                                    selected == null ? null : selected.id(),
                                    name.getText(),
                                    serverUrl,
                                    login.getText(),
                                    selected == null ? Set.of() : selected.roleCodes(),
                                    remember.isSelected(),
                                    defaultProfile.isSelected()),
                            secret,
                            remember.isSelected());
            formMode = FormMode.VIEW;
            refresh();
            profiles.getSelectionModel().select(value);
            status.setText("Профиль сохранён.");
            updateActions();
        } catch (Exception error) {
            StatusMessages.error(status, error.getMessage());
        } finally {
            Arrays.fill(secret, '\0');
            password.clear();
        }
    }

    @FXML
    private void delete() {
        ServerProfile value = profiles.getSelectionModel().getSelectedItem();
        if (context == null || value == null || formMode != FormMode.VIEW) return;
        var confirm =
                FxView.confirmation(
                        "Удалить профиль «" + value.name() + "»?",
                        ButtonType.YES,
                        ButtonType.NO);
        confirm.initOwner(profiles.getScene().getWindow());
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        try {
            context.profiles.delete(value);
            refresh();
            profiles.getSelectionModel().clearSelection();
            display(null);
            status.setText("Профиль удалён.");
            updateActions();
        } catch (Exception error) {
            StatusMessages.error(status, error.getMessage());
        }
    }

    private void updateActions() {
        if (name == null) return;
        boolean formOpen = formMode != FormMode.VIEW;
        boolean hasSelection = profiles.getSelectionModel().getSelectedItem() != null;
        name.setDisable(!formOpen);
        login.setDisable(!formOpen);
        password.setDisable(!formOpen);
        remember.setDisable(!formOpen);
        defaultProfile.setDisable(!formOpen);
        saveButton.setVisible(formOpen);
        saveButton.setManaged(formOpen);
        saveButton.setText("Сохранить");
        editButton.setDisable(!hasSelection || formOpen);
        deleteButton.setDisable(!hasSelection || formOpen);
        editMenuItem.setDisable(!hasSelection || formOpen);
        deleteMenuItem.setDisable(!hasSelection || formOpen);
    }

    private void prepareContextMenu(ContextMenuEvent event) {
        Node target = event.getPickResult().getIntersectedNode();
        while (target != null && !(target instanceof ListCell<?>)) target = target.getParent();
        if (target instanceof ListCell<?> cell) {
            if (cell.isEmpty()) profiles.getSelectionModel().clearSelection();
            else profiles.getSelectionModel().select(cell.getIndex());
        }
        updateActions();
    }
}
