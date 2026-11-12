package org.bike.workorder.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.function.Consumer;
import org.bike.api.ApiClient;
import org.bike.api.ApiException;

public final class HttpWorkOrderDefaultCommentApi {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PATH = "/api/work-order-default-comment";
    private final ApiClient api;

    public HttpWorkOrderDefaultCommentApi(ApiClient api) { this.api = api; }

    public void read(Consumer<String> success, Consumer<Exception> failure) {
        api.request("GET", PATH, null, response -> {
            try { success.accept(JSON.readTree(response.body()).path("comment").asText("")); }
            catch (Exception error) { failure.accept(error); }
        }, error -> {
            if (error instanceof ApiException apiError && apiError.status() == 404) success.accept(null);
            else failure.accept(error);
        });
    }

    public void save(String comment, boolean exists, Consumer<Void> success, Consumer<Exception> failure) {
        ObjectNode body = JSON.createObjectNode().put("comment", comment);
        api.request(exists ? "PUT" : "POST", PATH, body, response -> success.accept(null), failure);
    }
}
