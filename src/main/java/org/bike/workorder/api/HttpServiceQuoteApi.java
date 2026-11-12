package org.bike.workorder.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.bike.api.ApiClient;
import org.bike.workorder.model.ServiceQuote;
import org.bike.workorder.model.WorkOrderDraft.ServiceLine;

public final class HttpServiceQuoteApi implements ServiceQuoteApi {
    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final ApiClient api;

    public HttpServiceQuoteApi(ApiClient api) { this.api = Objects.requireNonNull(api); }

    @Override
    public void calculate(List<ServiceLine> lines, Consumer<ServiceQuote> success, Consumer<Exception> failure) {
        var body = JSON.valueToTree(Map.of("services", lines));
        body.path("services").forEach(line -> ((com.fasterxml.jackson.databind.node.ObjectNode) line)
                .retain("serviceId", "discountId", "priceWithoutDiscount", "priceWithDiscount", "serviceNameSnapshot"));
        api.request("POST", "/api/work-orders/services/quote", body, response -> {
            ServiceQuote quote;
            try {
                quote = JSON.readValue(response.body(), ServiceQuote.class);
                if (quote == null || quote.services() == null || quote.totalPrice() == null
                        || quote.totalWithDiscount() == null) throw new IOException("Пустой расчёт.");
            } catch (IOException exception) {
                failure.accept(new IOException("Не удалось прочитать расчёт услуг.", exception));
                return;
            }
            success.accept(quote);
        }, failure);
    }
}
