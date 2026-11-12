package org.bike.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;

/** Per-profile session cookie and CSRF state. */
public final class ServerSession {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private volatile HttpClient client;
    private ServerProfile profile;
    private String csrfHeader;
    private String csrfToken;

    public ServerSession(ServerProfile profile) {
        this(profile, null);
    }

    public ServerSession(ServerProfile profile, SSLContext sslContext) {
        this.profile = profile;
        this.client = createClient(sslContext);
    }

    public void updateSslContext(SSLContext sslContext) {
        this.client = createClient(sslContext);
    }

    private HttpClient createClient(SSLContext sslContext) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10));
        if (sslContext != null) clientBuilder.sslContext(sslContext);
        return clientBuilder.build();
    }

    public ServerProfile profile() {
        return profile;
    }

    public ServerProfile login(char[] password) throws IOException, InterruptedException {
        refreshCsrf();
        ObjectNode body = JSON.createObjectNode();
        body.put("login", profile.login());
        body.put("password", new String(password));
        HttpRequest request = HttpRequest.newBuilder(endpoint("/api/auth/login"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header(csrfHeader, csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) {
            throw new ApiException(401, "Логин или пароль неверный.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw apiError(response);
        }

        JsonNode user = JSON.readTree(response.body());
        Set<String> roles = new HashSet<>();
        JsonNode roleCodes = user.path("roleCodes");
        if (roleCodes.isArray()) {
            roleCodes.forEach(role -> roles.add(role.asText()));
        }
        profile = profile.withRoles(roles);
        refreshCsrf();
        return profile;
    }

    /** Reauthenticates once on 401, then repeats the original request once. */
    public HttpResponse<String> request(String method, String path, JsonNode body,
                                        Supplier<char[]> passwordSupplier,
                                        Consumer<ServerProfile> profileUpdated)
            throws IOException, InterruptedException {
        HttpResponse<String> response = sendOnce(method, path, body);
        if (response.statusCode() != 401) {
            return response;
        }

        char[] password = passwordSupplier.get();
        if (password == null || password.length == 0) {
            throw new ApiException(401, "Для продолжения работы требуется войти снова.");
        }
        try {
            login(password);
            profileUpdated.accept(profile);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
        response = sendOnce(method, path, body);
        if (response.statusCode() == 401) {
            throw new ApiException(401, "Сессия не восстановлена. Проверьте пароль профиля.");
        }
        return response;
    }

    private HttpResponse<String> sendOnce(String method, String path, JsonNode body)
            throws IOException, InterruptedException {
        String normalizedMethod = method.toUpperCase(java.util.Locale.ROOT);
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
        if ("GET".equals(normalizedMethod)) {
            builder.GET();
        } else {
            refreshCsrf();
            builder.header("Content-Type", "application/json")
                    .header(csrfHeader, csrfToken)
                    .method(normalizedMethod, HttpRequest.BodyPublishers.ofString(
                            body == null ? "{}" : JSON.writeValueAsString(body)));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void refreshCsrf() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint("/api/auth/csrf"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw apiError(response);
        }
        JsonNode token = JSON.readTree(response.body());
        csrfHeader = token.path("headerName").asText();
        csrfToken = token.path("token").asText();
        if (csrfHeader.isBlank() || csrfToken.isBlank()) {
            throw new IOException("Сервер вернул пустой CSRF-токен.");
        }
    }

    private URI endpoint(String path) {
        String base = profile.serverUrl().replaceAll("/+$", "");
        return URI.create(base + (path.startsWith("/") ? path : "/" + path));
    }

    private ApiException apiError(HttpResponse<String> response) {
        try {
            String detail = JSON.readTree(response.body()).path("detail").asText();
            if (!detail.isBlank()) return new ApiException(response.statusCode(), detail);
        } catch (IOException ignored) {
            // Fall back to a generic, localized message when the server response is not JSON.
        }
        return new ApiException(response.statusCode(), "Сервер вернул ошибку HTTP %d.".formatted(response.statusCode()));
    }

    public static final class ApiException extends IOException {
        private final int statusCode;

        public ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
