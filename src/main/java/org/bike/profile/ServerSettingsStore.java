package org.bike.profile;

import java.net.URI;
import java.util.prefs.Preferences;

/** Stores application-wide server connection settings for the current Windows user. */
public final class ServerSettingsStore {
    private final Preferences settings = Preferences.userNodeForPackage(ServerSettingsStore.class)
            .node("connection");

    public synchronized String serverUrl() {
        return settings.get("serverUrl", "").trim();
    }

    public synchronized String saveServerUrl(String value) {
        String normalized = normalizeAndValidate(value);
        settings.put("serverUrl", normalized);
        try {
            settings.flush();
        } catch (java.util.prefs.BackingStoreException exception) {
            throw new IllegalStateException("Не удалось сохранить адрес сервера.", exception);
        }
        return normalized;
    }

    private String normalizeAndValidate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Укажите адрес сервера.");
        }
        String normalized = value.trim().replaceAll("/+$", "");
        try {
            URI uri = URI.create(normalized);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Укажите полный адрес, например http://localhost:8080.");
        }
        return normalized;
    }
}
