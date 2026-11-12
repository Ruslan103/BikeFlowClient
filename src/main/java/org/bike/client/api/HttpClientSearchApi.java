package org.bike.client.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.bike.api.ApiClient;
import org.bike.client.api.dto.ClientData;

public final class HttpClientSearchApi implements ClientSearchApi {
    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final ApiClient api;

    public HttpClientSearchApi(ApiClient api) { this.api = Objects.requireNonNull(api); }

    @Override
    public void search(String phone, Consumer<List<ClientData>> success, Consumer<Exception> failure) {
        String path = "/api/clients/search?phone=" + URLEncoder.encode(phone, StandardCharsets.UTF_8);
        api.request("GET", path, null, response -> {
            List<ClientData> clients;
            try {
                clients = JSON.readValue(response.body(), JSON.getTypeFactory().constructCollectionType(List.class, ClientData.class));
                if (clients == null || clients.stream().anyMatch(Objects::isNull)) throw new IOException("Пустой ответ.");
            } catch (IOException exception) {
                failure.accept(new IOException("Не удалось прочитать данные клиентов.", exception));
                return;
            }
            success.accept(clients);
        }, failure);
    }
}
