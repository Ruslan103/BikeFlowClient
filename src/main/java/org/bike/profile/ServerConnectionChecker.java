package org.bike.profile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Checks whether the configured server responds to its public CSRF endpoint. */
public final class ServerConnectionChecker {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    public boolean isAvailable(String serverUrl) throws IOException, InterruptedException {
        String baseUrl = serverUrl.replaceAll("/+$", "");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/csrf"))
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }
}
