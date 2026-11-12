package org.bike.catalog.api;

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
import org.bike.catalog.api.dto.CreatePriceRequest;
import org.bike.catalog.api.dto.CreateServiceRequest;
import org.bike.catalog.api.dto.ServicePriceData;
import org.bike.catalog.api.dto.WorkshopServiceData;
import org.bike.catalog.api.dto.WorkshopServiceBatchData;
import org.bike.catalog.api.dto.UpdateServiceRequest;
import org.bike.catalog.api.dto.UpdatePriceRequest;

public final class HttpServiceCatalogApi implements ServiceCatalogApi {
    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final String PATH = "/api/services";
    private final ApiClient client;

    public HttpServiceCatalogApi(ApiClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public void listServices(Consumer<List<WorkshopServiceData>> success, Consumer<Exception> failure) {
        request("GET", PATH, null, JSON.getTypeFactory().constructCollectionType(List.class, WorkshopServiceData.class), success, failure);
    }

    @Override
    public void searchServices(String query, Consumer<List<WorkshopServiceData>> success,
                               Consumer<Exception> failure) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        request("GET", PATH + "/search?query=" + encodedQuery, null,
                JSON.getTypeFactory().constructCollectionType(List.class, WorkshopServiceData.class),
                success, failure);
    }

    @Override
    public void listServicesBatch(int page, int size, Consumer<WorkshopServiceBatchData> success,
                                  Consumer<Exception> failure) {
        request("GET", PATH + "/batch?page=" + page + "&size=" + size, null,
                JSON.constructType(WorkshopServiceBatchData.class), success, failure);
    }

    @Override
    public void findService(Long id, Consumer<WorkshopServiceData> success, Consumer<Exception> failure) {
        request("GET", PATH + "/" + id, null, JSON.constructType(WorkshopServiceData.class), success, failure);
    }

    @Override
    public void createService(CreateServiceRequest request, Consumer<WorkshopServiceData> success, Consumer<Exception> failure) {
        request("POST", PATH, request, JSON.constructType(WorkshopServiceData.class), success, failure);
    }

    @Override
    public void updateService(Long id, UpdateServiceRequest request,
                              Consumer<WorkshopServiceData> success, Consumer<Exception> failure) {
        request("PUT", PATH + "/" + id, request, JSON.constructType(WorkshopServiceData.class), success, failure);
    }

    @Override
    public void deleteService(Long id, Runnable success, Consumer<Exception> failure) {
        client.request("DELETE", PATH + "/" + id, null, response -> success.run(), failure);
    }

    @Override
    public void createPrice(Long serviceId, CreatePriceRequest request, Consumer<ServicePriceData> success, Consumer<Exception> failure) {
        request("POST", PATH + "/" + serviceId + "/prices", request, JSON.constructType(ServicePriceData.class), success, failure);
    }

    @Override
    public void updatePrice(Long serviceId, Long priceId, UpdatePriceRequest request,
                            Consumer<ServicePriceData> success, Consumer<Exception> failure) {
        request("PUT", PATH + "/" + serviceId + "/prices/" + priceId, request,
                JSON.constructType(ServicePriceData.class), success, failure);
    }

    @Override
    public void deletePrice(Long serviceId, Long priceId, Runnable success, Consumer<Exception> failure) {
        client.request("DELETE", PATH + "/" + serviceId + "/prices/" + priceId, null,
                response -> success.run(), failure);
    }

    @Override
    public void listPrices(Long serviceId, Consumer<List<ServicePriceData>> success, Consumer<Exception> failure) {
        request("GET", PATH + "/" + serviceId + "/prices", null,
                JSON.getTypeFactory().constructCollectionType(List.class, ServicePriceData.class), success, failure);
    }

    private <T> void request(String method, String path, Object body, JavaType type,
                             Consumer<T> success, Consumer<Exception> failure) {
        client.request(method, path, body == null ? null : JSON.valueToTree(body), response -> {
            T data;
            try {
                data = JSON.readValue(response.body(), type);
                if (data == null) throw new IOException("Сервер вернул пустой ответ.");
            } catch (IOException exception) {
                failure.accept(new IOException("Не удалось прочитать ответ каталога услуг.", exception));
                return;
            }
            success.accept(data);
        }, failure);
    }
}
