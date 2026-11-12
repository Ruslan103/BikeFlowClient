package org.bike.catalog.api;

import java.util.List;
import java.util.function.Consumer;
import org.bike.catalog.api.dto.CreatePriceRequest;
import org.bike.catalog.api.dto.CreateServiceRequest;
import org.bike.catalog.api.dto.ServicePriceData;
import org.bike.catalog.api.dto.WorkshopServiceData;
import org.bike.catalog.api.dto.WorkshopServiceBatchData;
import org.bike.catalog.api.dto.UpdateServiceRequest;
import org.bike.catalog.api.dto.UpdatePriceRequest;

/** Asynchronous catalog transport; callbacks run on the configured UI thread. */
public interface ServiceCatalogApi {
    void listServices(Consumer<List<WorkshopServiceData>> success, Consumer<Exception> failure);
    void searchServices(String query, Consumer<List<WorkshopServiceData>> success, Consumer<Exception> failure);
    void listServicesBatch(int page, int size, Consumer<WorkshopServiceBatchData> success, Consumer<Exception> failure);
    void findService(Long id, Consumer<WorkshopServiceData> success, Consumer<Exception> failure);
    void createService(CreateServiceRequest request, Consumer<WorkshopServiceData> success, Consumer<Exception> failure);
    void updateService(Long id, UpdateServiceRequest request, Consumer<WorkshopServiceData> success, Consumer<Exception> failure);
    void deleteService(Long id, Runnable success, Consumer<Exception> failure);
    void createPrice(Long serviceId, CreatePriceRequest request, Consumer<ServicePriceData> success, Consumer<Exception> failure);
    void updatePrice(Long serviceId, Long priceId, UpdatePriceRequest request, Consumer<ServicePriceData> success, Consumer<Exception> failure);
    void deletePrice(Long serviceId, Long priceId, Runnable success, Consumer<Exception> failure);
    void listPrices(Long serviceId, Consumer<List<ServicePriceData>> success, Consumer<Exception> failure);
}
