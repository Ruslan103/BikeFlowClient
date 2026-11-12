package org.bike.client.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import org.bike.api.ApiClient;
import org.bike.client.api.dto.BicycleCardRequest;
import org.bike.client.api.dto.ClientCardRequest;
import org.bike.client.api.dto.ClientBatchData;
import org.bike.client.api.dto.ClientData;
import org.bike.client.api.dto.ClientWorkOrderData;
import org.bike.client.api.dto.ClientSearchResult;
import org.bike.client.api.dto.ContactTypeData;

public final class HttpClientDirectoryApi implements ClientDirectoryApi {
    private static final ObjectMapper JSON = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final ApiClient api;
    public HttpClientDirectoryApi(ApiClient api) { this.api = java.util.Objects.requireNonNull(api); }
    public void preview(Long clientId, Long bicycleId, Long contactId, Consumer<org.bike.client.api.dto.ClientSelectionResponse> success, Consumer<Exception> failure) {
        var body = JSON.createObjectNode().put("clientId",clientId);
        if(bicycleId!=null)body.put("bicycleId",bicycleId);if(contactId!=null)body.put("contactId",contactId);
        request("POST","/api/work-orders/client-selection",body,JSON.constructType(org.bike.client.api.dto.ClientSelectionResponse.class),success,failure);
    }
    public void search(String query, Consumer<ClientSearchResult> success, Consumer<Exception> failure) {
        request("GET", "/api/clients?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8), null, JSON.constructType(ClientSearchResult.class), success, failure);
    }
    public void batch(int page, int size, Consumer<ClientBatchData> success, Consumer<Exception> failure) {
        request("GET", "/api/clients/batch?page=" + page + "&size=" + size, null,
                JSON.constructType(ClientBatchData.class), success, failure);
    }
    public void workOrders(Long clientId, Consumer<List<ClientWorkOrderData>> success,
                           Consumer<Exception> failure) {
        request("GET", "/api/clients/" + clientId + "/work-orders", null,
                JSON.getTypeFactory().constructCollectionType(List.class, ClientWorkOrderData.class),
                success, failure);
    }
    public void find(Long id, Consumer<ClientData> success, Consumer<Exception> failure) { request("GET", "/api/clients/"+id, null, JSON.constructType(ClientData.class), success, failure); }
    public void create(ClientCardRequest body, Consumer<ClientData> success, Consumer<Exception> failure) { request("POST", "/api/clients", body, JSON.constructType(ClientData.class), success, failure); }
    public void update(Long id, ClientCardRequest body, Consumer<ClientData> success, Consumer<Exception> failure) { request("PUT", "/api/clients/"+id, body, JSON.constructType(ClientData.class), success, failure); }
    public void delete(Long id, Runnable success, Consumer<Exception> failure) {
        api.request("DELETE", "/api/clients/" + id, null, response -> success.run(), failure);
    }
    public void createBicycle(Long id, BicycleCardRequest body, Consumer<ClientData> success, Consumer<Exception> failure) { request("POST", "/api/clients/"+id+"/bicycles", body, JSON.constructType(ClientData.class), success, failure); }
    public void updateBicycle(Long id, Long bike, BicycleCardRequest body, Consumer<ClientData> success, Consumer<Exception> failure) { request("PUT", "/api/clients/"+id+"/bicycles/"+bike, body, JSON.constructType(ClientData.class), success, failure); }
    public void deleteBicycle(Long id, Long bike, Long version, Consumer<ClientData> success, Consumer<Exception> failure) {
        api.request("DELETE", "/api/clients/"+id+"/bicycles/"+bike+"?version="+version, null,
                response -> find(id, success, failure), failure);
    }
    public void types(Consumer<List<ContactTypeData>> success, Consumer<Exception> failure) { request("GET", "/api/contact-types", null, JSON.getTypeFactory().constructCollectionType(List.class, ContactTypeData.class), success, failure); }
    private <T> void request(String method, String path, Object body, JavaType type, Consumer<T> success, Consumer<Exception> failure) {
        api.request(method, path, body == null ? null : JSON.valueToTree(body), response -> {
            T data;
            try {
                data = JSON.readValue(response.body(), type);
                if (data == null) throw new IOException("Пустой ответ.");
            } catch (IOException | IllegalArgumentException exception) {
                failure.accept(new IOException("Не удалось прочитать карточку клиента.", exception));
                return;
            }
            success.accept(data);
        }, failure);
    }
}
