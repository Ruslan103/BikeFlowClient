package org.bike.fx.settings;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;

import org.bike.fx.*;
import org.bike.profile.ServerCertificateClient;
import org.bike.profile.ServerProfile;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class SettingsController implements SectionController {
    @FXML private TextField url;
    @FXML private TextArea defaultComment;
    @FXML private Label formStatus, serverStatus;
    @FXML private Button saveServerButton, refreshCertificateButton;
    private FxContext context;
    private boolean defaultCommentExists;

    @FXML
    private void initialize() {
        StatusMessages.install(formStatus);
        StatusMessages.install(serverStatus);
    }

    public void bind(FxContext value) {
        context = value;
        url.setText(context.settings.serverUrl());
        context.defaultOrderComment.read(comment -> {
            defaultCommentExists = comment != null;
            defaultComment.setText(comment == null ? "" : comment);
        }, error -> StatusMessages.error(formStatus, error.getMessage()));
    }

    @FXML
    private void save() {
        try {
            String savedUrl = context.settings.saveServerUrl(url.getText());
            syncCertificate(savedUrl, true);
        } catch (Exception error) {
            StatusMessages.error(serverStatus, error.getMessage());
        }
    }

    @FXML
    private void refreshCertificate() {
        String serverUrl = context.settings.serverUrl();
        if (serverUrl.isBlank()) serverUrl = url.getText().trim();
        if (serverUrl.isBlank()) {
            StatusMessages.error(serverStatus, "Сначала сохраните адрес сервера.");
            return;
        }
        syncCertificate(serverUrl, false);
    }

    private void syncCertificate(String serverUrl, boolean addressWasSaved) {
        ServerProfile active = context.connection.profile();
        ServerProfile target = active == null
                ? new ServerProfile("settings-certificate", "Сервер", serverUrl, "", Set.of(), false, false)
                : active.withServerUrl(serverUrl);
        setCertificateActionsDisabled(true);
        serverStatus.setText("Загружается сертификат сервера…");
        CompletableFuture.supplyAsync(() -> {
            try {
                return ServerCertificateClient.download(target, context.profiles.trustedServerSslContext(target));
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        }).whenComplete((downloaded, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause() : failure;
                setCertificateActionsDisabled(false);
                String prefix = addressWasSaved ? "Адрес сохранён, но сертификат не обновлён: " : "Сертификат не обновлён: ";
                StatusMessages.error(serverStatus, prefix + cause.getMessage());
                return;
            }
            try {
                boolean unchanged = context.profiles.hasTrustedServerCertificate(target, downloaded.certificate());
                if (!unchanged && !confirmCertificateTrust(downloaded.fingerprint(), downloaded.downloadedOverHttp())) {
                    setCertificateActionsDisabled(false);
                    serverStatus.setText("Сертификат не добавлен: сверка отпечатка не подтверждена.");
                    return;
                }
                if (!unchanged) context.profiles.importServerCertificate(target, downloaded.certificate());
                context.connection.refreshTrustedServerContext(target, () -> {
                    setCertificateActionsDisabled(false);
                    serverStatus.setText((addressWasSaved ? "Адрес сохранён. " : "")
                            + (unchanged ? "Сертификат уже актуален." : "Сертификат обновлён.")
                            + " Текущая сессия клиента сохранена.");
                }, error -> {
                    setCertificateActionsDisabled(false);
                    StatusMessages.error(serverStatus, "Сертификат сохранён, но не удалось обновить TLS-клиент: "
                            + error.getMessage());
                });
            } catch (Exception error) {
                setCertificateActionsDisabled(false);
                StatusMessages.error(serverStatus, error.getMessage());
            }
        }));
    }

    private boolean confirmCertificateTrust(String fingerprint, boolean downloadedOverHttp) {
        String message = "SHA-256: " + fingerprint + "\n\n"
                + "Сверьте отпечаток с кабинетом сервера или другим доверенным источником перед добавлением."
                + (downloadedOverHttp
                        ? "\n\nСертификат получен по HTTP; без сверки отпечатка его можно подменить в сети."
                        : "");
        var confirmation = FxView.confirmation(message, javafx.scene.control.ButtonType.YES,
                javafx.scene.control.ButtonType.NO);
        confirmation.setHeaderText("Подтвердите сертификат сервера");
        return confirmation.showAndWait().orElse(javafx.scene.control.ButtonType.NO)
                == javafx.scene.control.ButtonType.YES;
    }

    private void setCertificateActionsDisabled(boolean disabled) {
        saveServerButton.setDisable(disabled);
        refreshCertificateButton.setDisable(disabled);
    }

    @FXML
    private void saveDefaultComment() {
        context.defaultOrderComment.save(defaultComment.getText(), defaultCommentExists, ignored -> {
            defaultCommentExists = true;
            formStatus.setText("Комментарий по умолчанию сохранён.");
        }, error -> StatusMessages.error(formStatus, error.getMessage()));
    }
}

