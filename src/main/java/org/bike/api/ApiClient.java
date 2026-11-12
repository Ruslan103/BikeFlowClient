package org.bike.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

/** Executes authenticated requests asynchronously; the implementation dispatches callbacks to its UI thread. */
@FunctionalInterface
public interface ApiClient {
    void request(String method, String path, JsonNode body,
                 Consumer<HttpResponse<String>> onSuccess, Consumer<Exception> onFailure);
}
