package org.bike.profile;

import java.util.Set;

public record ServerProfile(
        String id,
        String name,
        String serverUrl,
        String login,
        Set<String> roleCodes,
        boolean rememberPassword,
        boolean defaultProfile
) {
    public ServerProfile {
        roleCodes = roleCodes == null ? Set.of() : Set.copyOf(roleCodes);
    }

    public ServerProfile withRoles(Set<String> roles) {
        return new ServerProfile(id, name, serverUrl, login, roles, rememberPassword, defaultProfile);
    }

    public ServerProfile withServerUrl(String url) {
        return new ServerProfile(id, name, url, login, roleCodes, rememberPassword, defaultProfile);
    }

    @Override
    public String toString() {
        String roles = roleCodes.isEmpty() ? "роль не загружена" : String.join(", ", roleCodes);
        return "%s (%s) · %s".formatted(name, login, roles);
    }
}
