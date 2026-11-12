package org.bike.client.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import org.bike.api.ApiClient;
import org.bike.client.api.dto.ContactTypeData;

public final class HttpContactTypeApi implements ContactTypeApi {
    private static final ObjectMapper JSON = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final ApiClient api;
    public HttpContactTypeApi(ApiClient api) { this.api = java.util.Objects.requireNonNull(api); }
    @Override public void list(Consumer<List<ContactTypeData>> success, Consumer<Exception> failure) {
        api.request("GET", "/api/contact-types", null, response -> {
            List<ContactTypeData> data;
            try {
                data = JSON.readValue(response.body(), JSON.getTypeFactory().constructCollectionType(List.class, ContactTypeData.class));
                if (data == null) throw new IOException("Пустой ответ.");
            } catch (IOException exception) {
                failure.accept(new IOException("Не удалось прочитать типы контактов.", exception));
                return;
            }
            success.accept(data);
        }, failure);
    }
}
