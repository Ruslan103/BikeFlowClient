package org.bike.catalog.presentation;

import java.util.List;
import java.util.Objects;
import org.bike.catalog.api.ServiceCatalogApi;
import org.bike.catalog.api.dto.CreatePriceRequest;
import org.bike.catalog.api.dto.CreateServiceRequest;
import org.bike.catalog.api.dto.ServicePriceData;
import org.bike.catalog.api.dto.WorkshopServiceData;

/** Coordinates view updates and requests; all pricing rules belong to the server. */
public final class ServiceCatalogController {
    public interface View {
        void setBusy(boolean busy);
        void showServices(List<WorkshopServiceData> services, Long selectedId);
        void showPrices(WorkshopServiceData service, List<ServicePriceData> prices);
        void showError(String message);
        void creationSucceeded();
    }

    private final ServiceCatalogApi api;
    private final View view;
    private boolean busy;

    public ServiceCatalogController(ServiceCatalogApi api, View view) {
        this.api = Objects.requireNonNull(api);
        this.view = Objects.requireNonNull(view);
    }

    public void refresh(Long selectedId) {
        if (busy) return;
        setBusy(true);
        loadServices(selectedId);
    }

    public void createService(CreateServiceRequest request) {
        if (busy) return;
        setBusy(true);
        api.createService(request, created -> {
            view.creationSucceeded();
            loadServices(created.id());
        }, this::failed);
    }

    public void createPrice(WorkshopServiceData service, CreatePriceRequest request) {
        if (busy) return;
        setBusy(true);
        api.createPrice(service.id(), request, created -> {
            view.creationSucceeded();
            loadServices(service.id());
        }, this::failed);
    }

    public void showPrices(WorkshopServiceData service) {
        if (busy) return;
        setBusy(true);
        api.listPrices(service.id(), prices -> {
            setBusy(false);
            view.showPrices(service, prices);
        }, this::failed);
    }

    private void loadServices(Long selectedId) {
        api.listServices(services -> {
            view.showServices(services, selectedId);
            setBusy(false);
        }, this::failed);
    }

    private void failed(Exception error) {
        setBusy(false);
        view.showError(error.getMessage());
    }

    private void setBusy(boolean value) {
        busy = value;
        view.setBusy(value);
    }
}
