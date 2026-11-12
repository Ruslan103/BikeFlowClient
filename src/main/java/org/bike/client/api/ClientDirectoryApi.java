package org.bike.client.api;

import java.util.List;
import java.util.function.Consumer;
import org.bike.client.api.dto.BicycleCardRequest;
import org.bike.client.api.dto.ClientCardRequest;
import org.bike.client.api.dto.ClientBatchData;
import org.bike.client.api.dto.ClientData;
import org.bike.client.api.dto.ClientWorkOrderData;
import org.bike.client.api.dto.ClientSearchResult;
import org.bike.client.api.dto.ContactTypeData;

/** Authenticated asynchronous client calls. Callbacks run on the configured UI thread. */
public interface ClientDirectoryApi {
    void preview(Long clientId, Long bicycleId, Long contactId, Consumer<org.bike.client.api.dto.ClientSelectionResponse> success, Consumer<Exception> failure);
    void search(String query, Consumer<ClientSearchResult> success, Consumer<Exception> failure);
    void batch(int page, int size, Consumer<ClientBatchData> success, Consumer<Exception> failure);
    void workOrders(Long clientId, Consumer<List<ClientWorkOrderData>> success, Consumer<Exception> failure);
    void find(Long id, Consumer<ClientData> success, Consumer<Exception> failure);
    void create(ClientCardRequest request, Consumer<ClientData> success, Consumer<Exception> failure);
    void update(Long id, ClientCardRequest request, Consumer<ClientData> success, Consumer<Exception> failure);
    void delete(Long id, Runnable success, Consumer<Exception> failure);
    void createBicycle(Long clientId, BicycleCardRequest request, Consumer<ClientData> success, Consumer<Exception> failure);
    void updateBicycle(Long clientId, Long bicycleId, BicycleCardRequest request, Consumer<ClientData> success, Consumer<Exception> failure);
    void deleteBicycle(Long clientId, Long bicycleId, Long version, Consumer<ClientData> success, Consumer<Exception> failure);
    void types(Consumer<List<ContactTypeData>> success, Consumer<Exception> failure);
}
