package org.bike.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.StringJoiner;

/** Converts server ProblemDetail responses into messages without interpreting business rules. */
public final class ApiResponseErrors {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ApiResponseErrors() { }

    public static IOException from(HttpResponse<String> response) {
        String fallback = "Сервер вернул ошибку HTTP %d.".formatted(response.statusCode());
        try {
            JsonNode body = JSON.readTree(response.body());
            if (body == null) return new IOException(fallback);
            StringJoiner message = new StringJoiner("\n");
            String detail = body.path("detail").asText("");
            message.add(detail.isBlank() ? fallback : detail);
            JsonNode errors = body.path("errors");
            if (errors.isArray()) errors.forEach(error -> message.add(error.asText()));
            return new ApiException(response.statusCode(), body.path("code").asText(""), message.toString(), body.path("searchQuery").asText(null));
        } catch (IOException exception) {
            return new IOException(fallback, exception);
        }
    }
}
