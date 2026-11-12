package org.bike.workorder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bike.api.ApiClient;
import org.bike.workorder.model.WorkOrder;
import org.bike.workorder.model.WorkOrderDraft;

public final class WorkOrderService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PATH = "/api/work-orders";
    private final ApiClient api;

    public WorkOrderService(ApiClient api) {
        this.api = java.util.Objects.requireNonNull(api);
    }

    public void load(String number, Consumer<List<WorkOrder>> success, Consumer<Exception> failure) {
        api.request(
                "GET",
                path(number),
                null,
                response -> {
                    List<WorkOrder> result;
                    try {
                        JsonNode orders = JSON.readTree(response.body());
                        if (!orders.isArray()) {
                            throw new IOException("Ожидался список бланков.");
                        }
                        result = new ArrayList<>(orders.size());
                        for (JsonNode order : orders) result.add(parseWorkOrder(order));
                    } catch (IOException | RuntimeException exception) {
                        failure.accept(
                                new IOException("Сервер вернул некорректный список бланков.", exception));
                        return;
                    }
                    success.accept(List.copyOf(result));
                },
                failure);
    }

    public void create(WorkOrderDraft draft, Consumer<WorkOrder> success, Consumer<Exception> failure) {
        save("POST", PATH, draft, success, failure);
    }

    public void update(String number, WorkOrderDraft draft, Consumer<WorkOrder> success, Consumer<Exception> failure) {
        save("PUT", path(number), draft, success, failure);
    }

    private void save(String method, String path, WorkOrderDraft draft,
                      Consumer<WorkOrder> success, Consumer<Exception> failure) {
        if (draft.clientId() == null || (draft.clientBicycleId() == null && draft.customerParts().isEmpty())) {
            failure.accept(new IllegalStateException("Выберите клиента и добавьте велосипед или деталь клиента."));
            return;
        }
        com.fasterxml.jackson.databind.node.ObjectNode body = JSON.valueToTree(draft);
        body.remove(java.util.List.of("client", "details", "snapshot"));
        body.withArray("services").forEach(line -> ((com.fasterxml.jackson.databind.node.ObjectNode) line)
                .retain("serviceId", "discountId", "priceWithDiscount", "priceWithoutDiscount", "serviceNameSnapshot"));
        body.withArray("products").forEach(line -> ((com.fasterxml.jackson.databind.node.ObjectNode) line)
                .retain("productId", "newProduct", "quantity", "discountId", "priceWithoutDiscount", "priceWithDiscount", "supplyStatus"));
        body.withArray("customerParts").forEach(line -> ((com.fasterxml.jackson.databind.node.ObjectNode) line).remove("id"));
        request(method, path, body, success, failure);
    }

    private String path(String number) {
        return PATH + "/" + URLEncoder.encode(number, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void request(String method, String path, JsonNode body,
                         Consumer<WorkOrder> success, Consumer<Exception> failure) {
        api.request(method, path, body, response -> {
            WorkOrder order;
            try {
                JsonNode node = JSON.readTree(response.body());
                order = parseWorkOrder(node);
            } catch (IOException | RuntimeException exception) {
                failure.accept(new IOException("Сервер вернул некорректные данные бланка.", exception));
                return;
            }
            success.accept(order);
        }, failure);
    }

    private WorkOrder parseWorkOrder(JsonNode node) throws IOException {
        WorkOrderDraft draft = JSON.readerFor(WorkOrderDraft.class)
                .without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(node);
        return new WorkOrder(
                node.path("blankNumber").asText(),
                Instant.parse(node.path("createdAt").asText())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime(),
                draft,
                node.path("trelloCard").asText(null));
    }
}
