package org.bike.fx;

import org.bike.catalog.api.HttpServiceCatalogApi;
import org.bike.client.api.HttpClientDirectoryApi;
import org.bike.discount.api.HttpDiscountApi;
import org.bike.fx.api.FxConnection;
import org.bike.profile.ProfileStore;
import org.bike.profile.ServerSettingsStore;
import org.bike.service.BlankNumberService;
import org.bike.trello.api.HttpTrelloApi;
import org.bike.workorder.api.HttpServiceQuoteApi;
import org.bike.workorder.service.WorkOrderService;
import org.bike.workorder.api.HttpWorkOrderDefaultCommentApi;

public final class FxContext implements AutoCloseable {
    public final ProfileStore profiles = new ProfileStore();
    public final ServerSettingsStore settings = new ServerSettingsStore();
    public final FxConnection connection = new FxConnection(profiles);
    public final HttpClientDirectoryApi clients = new HttpClientDirectoryApi(connection);
    public final HttpServiceCatalogApi catalog = new HttpServiceCatalogApi(connection);
    public final HttpDiscountApi discounts = new HttpDiscountApi(connection);
    public final HttpTrelloApi trello = new HttpTrelloApi(connection);
    public final HttpServiceQuoteApi quotes = new HttpServiceQuoteApi(connection);
    public final WorkOrderService orders = new WorkOrderService(connection);
    public final HttpWorkOrderDefaultCommentApi defaultOrderComment = new HttpWorkOrderDefaultCommentApi(connection);
    public final BlankNumberService numbers = new BlankNumberService(connection);

    @Override
    public void close() {
        connection.close();
    }
}
