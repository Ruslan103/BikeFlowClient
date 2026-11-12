package org.bike.fx.profile;

import javafx.fxml.FXML;
import javafx.scene.control.*;

import org.bike.fx.FxContext;
import org.bike.profile.ServerProfile;

import java.util.Arrays;
import java.util.function.Consumer;

public final class LoginController {
    @FXML private Label name;
    @FXML private Label status;
    @FXML private PasswordField password;
    @FXML private Button loginButton;
    private FxContext context;
    private ServerProfile profile;
    private Consumer<ServerProfile> success;
    private boolean busy;

    @FXML
    private void initialize() {
        org.bike.fx.StatusMessages.install(status);
    }

    public boolean canClose() {
        return !busy;
    }

    public void bind(FxContext context, ServerProfile profile, Consumer<ServerProfile> success) {
        this.context = context;
        this.profile = profile;
        this.success = success;
        name.setText(profile.name() + " · " + profile.login());
        context.profiles
                .savedPassword(profile)
                .ifPresent(
                        secret -> {
                            password.setText(new String(secret));
                            Arrays.fill(secret, '\0');
                        });
    }

    @FXML
    private void login() {
        if (context == null) return;
        char[] secret = password.getText().toCharArray();
        busy = true;
        loginButton.setDisable(true);
        password.setDisable(true);
        status.setText("Подключение…");
        context.connection.login(
                profile,
                secret,
                value -> {
                    busy = false;
                    context.profiles.save(value, secret, value.rememberPassword());
                    Arrays.fill(secret, '\0');
                    password.clear();
                    success.accept(value);
                },
                error -> {
                    busy = false;
                    Arrays.fill(secret, '\0');
                    loginButton.setDisable(false);
                    password.setDisable(false);
                    org.bike.fx.StatusMessages.error(status, error.getMessage());
                });
    }
}
