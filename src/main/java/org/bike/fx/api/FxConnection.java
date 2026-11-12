package org.bike.fx.api;

import com.fasterxml.jackson.databind.JsonNode;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

import org.bike.api.ApiClient;
import org.bike.api.ApiResponseErrors;
import org.bike.profile.ProfileStore;
import org.bike.profile.ServerProfile;
import org.bike.profile.ServerSession;

import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.net.URI;

/** Serializes cookie/CSRF operations and delivers all callbacks on the JavaFX thread. */
public final class FxConnection implements ApiClient, AutoCloseable {
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(Thread.ofVirtual().name("server-", 0).factory());
    private final ProfileStore profiles;
    private final ReadOnlyBooleanWrapper connected = new ReadOnlyBooleanWrapper(false);
    private volatile ServerSession session;
    private long generation;

    public FxConnection(ProfileStore profiles) {
        this.profiles = profiles;
    }

    public ReadOnlyBooleanProperty connectedProperty() {
        return connected.getReadOnlyProperty();
    }

    public ServerProfile profile() {
        return session == null ? null : session.profile();
    }

    public void refreshTrustedServerContext(
            ServerProfile certificateProfile, Runnable success, Consumer<Exception> failure) {
        executor.submit(() -> {
            try {
                ServerSession active = session;
                if (active != null && sameHost(active.profile().serverUrl(), certificateProfile.serverUrl())) {
                    active.updateSslContext(profiles.trustedServerSslContext(active.profile()));
                }
                Platform.runLater(success);
            } catch (Exception error) {
                Platform.runLater(() -> failure.accept(error));
            }
        });
    }

    private boolean sameHost(String firstUrl, String secondUrl) {
        String firstHost = URI.create(firstUrl).getHost();
        String secondHost = URI.create(secondUrl).getHost();
        return firstHost != null && secondHost != null && firstHost.equalsIgnoreCase(secondHost);
    }

    public void login(
            ServerProfile profile,
            char[] password,
            Consumer<ServerProfile> success,
            Consumer<Exception> failure) {
        long token = ++generation;
        connected.set(false);
        char[] secret = password.clone();
        executor.submit(
                () -> {
                    try {
                        ServerSession candidate = new ServerSession(profile, profiles.trustedServerSslContext(profile));
                        ServerProfile authenticated = candidate.login(secret);
                        Platform.runLater(
                                () -> {
                                    if (generation == token) {
                                        session = candidate;
                                        connected.set(true);
                                        success.accept(authenticated);
                                    }
                                });
                    } catch (Exception error) {
                        Platform.runLater(
                                () -> {
                                    if (generation == token) failure.accept(error);
                                });
                    } finally {
                        Arrays.fill(secret, '\0');
                    }
                });
    }

    @Override
    public void request(
            String method,
            String path,
            JsonNode body,
            Consumer<HttpResponse<String>> success,
            Consumer<Exception> failure) {
        ServerSession active = session;
        long token = generation;
        if (active == null || !connected.get()) {
            failure.accept(new IllegalStateException("Войдите в профиль сервера."));
            return;
        }
        executor.submit(
                () -> {
                    try {
                        var response =
                                active.request(
                                        method,
                                        path,
                                        body,
                                        () -> profiles.savedPassword(active.profile()).orElse(null),
                                        updated -> {});
                        if (response.statusCode() < 200 || response.statusCode() >= 300)
                            throw ApiResponseErrors.from(response);
                        Platform.runLater(
                                () -> {
                                    if (generation == token) success.accept(response);
                                });
                    } catch (Exception error) {
                        Platform.runLater(
                                () -> {
                                    if (generation != token) return;
                                    if (error instanceof java.net.ConnectException
                                            || error instanceof java.net.http.HttpTimeoutException
                                            || error instanceof org.bike.api.ApiException api
                                                    && api.status() == 401
                                            || error
                                                            instanceof
                                                            ServerSession.ApiException sessionError
                                                    && sessionError.statusCode() == 401) {
                                        connected.set(false);
                                    }
                                    failure.accept(error);
                                });
                    }
                });
    }

    @Override
    public void close() {
        generation++;
        session = null;
        connected.set(false);
        executor.shutdownNow();
    }
}
