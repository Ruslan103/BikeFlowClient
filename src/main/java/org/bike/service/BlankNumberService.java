package org.bike.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import org.bike.api.ApiClient;

/** Obtains a new blank number from the server, preserving leading zeroes. */
public final class BlankNumberService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ApiClient apiClient;

    public BlankNumberService(ApiClient apiClient) {
        this.apiClient = Objects.requireNonNull(apiClient);
    }

    public void generate(Consumer<String> onSuccess, Consumer<Exception> onFailure) {
        apiClient.request("POST", "/api/work-orders/number", null, response -> {
            String number;
            try {
                JsonNode body = JSON.readTree(response.body());
                JsonNode value = body == null ? null : body.get("number");
                if (value == null || !value.isTextual() || value.textValue().isBlank()) {
                    throw new IOException("Сервер вернул пустой или некорректный номер бланка.");
                }
                number = value.textValue();
            } catch (IOException exception) {
                onFailure.accept(exception);
                return;
            }
            onSuccess.accept(number);
        }, onFailure);
    }
}
