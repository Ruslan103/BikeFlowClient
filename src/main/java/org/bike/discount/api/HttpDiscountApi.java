package org.bike.discount.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.bike.api.ApiClient;
import org.bike.discount.api.dto.DiscountData;
import org.bike.discount.api.dto.DiscountBatchData;
import org.bike.discount.api.dto.DiscountRequest;

public final class HttpDiscountApi implements DiscountApi {
    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final String PATH = "/api/discounts";
    private final ApiClient client;

    public HttpDiscountApi(ApiClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public void list(Consumer<List<DiscountData>> success, Consumer<Exception> failure) {
        request("GET", PATH, null, JSON.getTypeFactory().constructCollectionType(List.class, DiscountData.class), success, failure);
    }

    @Override
    public void search(String query, Consumer<List<DiscountData>> success, Consumer<Exception> failure) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        request("GET", PATH + "/search?query=" + encodedQuery, null,
                JSON.getTypeFactory().constructCollectionType(List.class, DiscountData.class), success, failure);
    }

    @Override
    public void listBatch(int page, int size, Consumer<DiscountBatchData> success, Consumer<Exception> failure) {
        request("GET", PATH + "/batch?page=" + page + "&size=" + size, null,
                JSON.constructType(DiscountBatchData.class), success, failure);
    }

    @Override
    public void create(DiscountRequest request, Consumer<DiscountData> success, Consumer<Exception> failure) {
        request("POST", PATH, request, JSON.constructType(DiscountData.class), success, failure);
    }

    @Override
    public void update(Long id, DiscountRequest request, Consumer<DiscountData> success, Consumer<Exception> failure) {
        request("PUT", PATH + "/" + id, request, JSON.constructType(DiscountData.class), success, failure);
    }

    @Override
    public void delete(Long id, Runnable success, Consumer<Exception> failure) {
        client.request("DELETE", PATH + "/" + id, null, response -> success.run(), failure);
    }

    private <T> void request(String method, String path, Object body, JavaType type,
                             Consumer<T> success, Consumer<Exception> failure) {
        client.request(method, path, body == null ? null : JSON.valueToTree(body), response -> {
            T data;
            try {
                data = JSON.readValue(response.body(), type);
                if (data == null) throw new IOException("Сервер вернул пустой ответ.");
            } catch (IOException exception) {
                failure.accept(new IOException("Не удалось прочитать ответ справочника скидок.", exception));
                return;
            }
            success.accept(data);
        }, failure);
    }
}
