package org.bike.fx.shell;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import org.bike.fx.*;
import org.bike.fx.client.ClientsController;
import org.bike.fx.workorder.WorkOrderController;
import org.bike.profile.ServerProfile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class ShellController {
    @FXML private StackPane content;
    @FXML private ComboBox<ServerProfile> profile;
    private SearchableComboBox<ServerProfile> profileOptions;
    @FXML private Label connectionLabel;
    @FXML private HBox navigation;
    private FxContext context;
    private final Map<String, FxView.Loaded<SectionController>> pages = new HashMap<>();
    private SectionController current;
    private String currentPage;

    public void bind(FxContext value) {
        context = value;
        profileOptions = SearchableComboBox.install(profile, ShellController::profileText);
        reloadProfiles();
        context.profiles.startupProfile().ifPresent(profile::setValue);
        context.connection
                .connectedProperty()
                .addListener(
                        (observable, old, connected) -> {
                            profile.pseudoClassStateChanged(
                                    javafx.css.PseudoClass.getPseudoClass("disconnected"),
                                    !connected);
                            profile.setTooltip(
                                    new Tooltip(
                                            connected
                                                    ? "Подключено к серверу"
                                                    : "Нет подключения к серверу"));
                            connectionLabel.setText(connected ? "" : "Нет подключения");
                        });
        profile.pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("disconnected"), true);
        show("Profiles");
        javafx.application.Platform.runLater(this::autoLoginStartupProfile);
    }

    private void autoLoginStartupProfile() {
        if (context == null) return;
        var startupProfile = context.profiles.startupProfile();
        if (startupProfile.isEmpty()) return;
        String configuredUrl = context.settings.serverUrl();
        if (configuredUrl.isBlank()) {
            connectionLabel.setText("Укажите адрес сервера в настройках");
            return;
        }

        ServerProfile selected = startupProfile.get();
        if (!selected.defaultProfile() && context.profiles.profiles().size() > 1) return;
        if (!selected.rememberPassword()) return;

        java.util.Optional<char[]> savedPassword;
        try {
            savedPassword = context.profiles.savedPassword(selected);
        } catch (Exception error) {
            connectionLabel.setText("Не удалось прочитать сохранённый пароль");
            connectionLabel.setTooltip(new Tooltip(error.getMessage()));
            return;
        }
        if (savedPassword.isEmpty()) return;

        char[] password = savedPassword.get();
        ServerProfile target = selected.withServerUrl(configuredUrl);
        connectionLabel.setText("Подключение к серверу…");
        connectionLabel.setTooltip(new Tooltip("Автоматический вход в профиль «" + selected.name() + "»"));

        context.connection.login(target, password, authenticated -> {
            try {
                ServerProfile saved = context.profiles.save(
                        authenticated, password, authenticated.rememberPassword());
                reloadProfiles();
                profile.getSelectionModel().select(saved);
                show("WorkOrder");
            } catch (Exception error) {
                connectionLabel.setText("Подключено, профиль не обновлён");
                connectionLabel.setTooltip(new Tooltip(error.getMessage()));
                show("WorkOrder");
            } finally {
                Arrays.fill(password, '\0');
            }
        }, error -> {
            Arrays.fill(password, '\0');
            connectionLabel.setText("Автовход не выполнен");
            connectionLabel.setTooltip(new Tooltip(error.getMessage()));
        });
    }

    public void reloadProfiles() {
        if (context != null) {
            var selected = profile.getValue();
            profileOptions.setItems(context.profiles.profiles());
            if (selected != null)
                profile.getItems().stream()
                        .filter(p -> p.id().equals(selected.id()))
                        .findFirst()
                        .ifPresent(profile::setValue);
        }
    }

    private static String profileText(ServerProfile profile) {
        return profile == null
                ? "Выберите профиль"
                : profile.name() + " · " + String.join(", ", profile.roleCodes());
    }

    @FXML
    private void login() {
        if (context == null || profile.getValue() == null) return;
        var selected = profile.getValue();
        String configuredUrl = context.settings.serverUrl();
        if (configuredUrl.isBlank()) {
            connectionLabel.setText("Укажите адрес сервера в Настройки → Настройки сервера");
            return;
        }
        ServerProfile targetProfile = selected.withServerUrl(configuredUrl);
        var active = context.connection.profile();
        boolean reconnect =
                active != null
                        && active.id().equals(targetProfile.id())
                        && active.serverUrl().equals(targetProfile.serverUrl());
        if (!reconnect && current != null && !current.canLeave()) return;
        String destination =
                currentPage == null || currentPage.equals("Profiles") ? "WorkOrder" : currentPage;
        FxView.Loaded<org.bike.fx.profile.LoginController> view = FxView.load("Login");
        var stage = FxView.dialog(content.getScene().getWindow(), "Вход в профиль", view.root());
        view.controller()
                .bind(
                        context,
                        targetProfile,
                        authenticated -> {
                            stage.close();
                            reloadProfiles();
                            profile.getSelectionModel().select(authenticated);
                            if (reconnect && current != null) current.refresh();
                            else {
                                pages.values().forEach(page -> page.controller().dispose());
                                pages.clear();
                                current = null;
                                show(destination);
                            }
                        });
        stage.setOnCloseRequest(
                event -> {
                    if (!view.controller().canClose()) event.consume();
                });
        stage.showAndWait();
    }

    private void show(String name) {
        if (name.equals(currentPage)) return;
        if (current != null && !current.canLeave()) return;
        var view =
                pages.computeIfAbsent(
                        name,
                        key -> {
                            FxView.Loaded<SectionController> loaded = FxView.load(key);
                            loaded.controller().bind(context);
                            return loaded;
                        });
        if (view.controller() instanceof ClientsController clientsController) {
            clientsController.setWorkOrderOpener(this::openClientWorkOrder);
        }
        content.getChildren().setAll(view.root());
        current = view.controller();
        currentPage = name;
        current.refresh();
        if (name.equals("Profiles")) reloadProfiles();
    }

    private void openClientWorkOrder(String number) {
        if (current != null && !current.canLeave()) return;
        show("WorkOrder");
        FxView.Loaded<SectionController> workOrderPage = pages.get("WorkOrder");
        if (workOrderPage != null && workOrderPage.controller() instanceof WorkOrderController controller) {
            controller.openClientWorkOrder(number);
        }
    }

    @FXML
    private void workOrder() {
        show("WorkOrder");
    }

    @FXML
    private void clients() {
        show("Clients");
    }

    @FXML
    private void catalog() {
        show("Catalog");
    }

    @FXML
    private void discounts() {
        show("Discounts");
    }

    @FXML
    private void profiles() {
        show("Profiles");
    }

    @FXML
    private void settings() {
        show("Settings");
    }

    @FXML
    private void toggleNavigation() {
        navigation.setVisible(!navigation.isVisible());
        navigation.setManaged(navigation.isVisible());
    }

    public boolean canClose() {
        return current == null || current.canLeave();
    }
}
