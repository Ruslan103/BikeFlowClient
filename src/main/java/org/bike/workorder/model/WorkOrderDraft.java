package org.bike.workorder.model;

import java.math.BigDecimal;
import java.util.List;

public record WorkOrderDraft(Long clientId, Long clientBicycleId, Long clientContactId,
        String reportedIssue, String clientComment, String internalComment,
        String contactMethod, String contactValue, List<ServiceLine> services, String statusCode,
        WorkOrderDetails details, String blankNumber, String createdAt, org.bike.client.api.dto.ClientData client,
        ClientSnapshotData snapshot, String snapshotAction, Long expectedClientVersion, Long expectedBicycleVersion, List<com.fasterxml.jackson.databind.JsonNode> products, List<com.fasterxml.jackson.databind.JsonNode> customerParts, String bicycleConditionOnReceipt) {
    public WorkOrderDraft(Long clientId, Long clientBicycleId, Long clientContactId,
            String reportedIssue, String clientComment, String internalComment, String contactMethod,
            String contactValue, List<ServiceLine> services, String statusCode, WorkOrderDetails details,
            String blankNumber, String createdAt, org.bike.client.api.dto.ClientData client,
            ClientSnapshotData snapshot, String snapshotAction, Long expectedClientVersion, Long expectedBicycleVersion) {
        this(clientId, clientBicycleId, clientContactId, reportedIssue, clientComment, internalComment,
                contactMethod, contactValue, services, statusCode, details, blankNumber, createdAt, client,
                snapshot, snapshotAction, expectedClientVersion, expectedBicycleVersion, List.of(), List.of(), null);
    }
    public WorkOrderDraft(Long clientId, Long clientBicycleId, Long clientContactId, String reportedIssue,
            String clientComment, String internalComment, String contactMethod, String contactValue,
            List<ServiceLine> services, String statusCode, WorkOrderDetails details, String blankNumber, String createdAt,
            org.bike.client.api.dto.ClientData client) {
        this(clientId, clientBicycleId, clientContactId, reportedIssue, clientComment, internalComment,
                contactMethod, contactValue, services, statusCode, details, blankNumber, createdAt,
                client, null, "KEEP", null, null);
    }
    public WorkOrderDraft(Long clientId, Long clientBicycleId, Long clientContactId, String reportedIssue,
            String clientComment, String internalComment, String contactMethod, String contactValue,
            List<ServiceLine> services, String statusCode) {
        this(clientId, clientBicycleId, clientContactId, reportedIssue, clientComment, internalComment,
                contactMethod, contactValue, services, statusCode, null, null, null, null);
    }
    public WorkOrderDraft {
        services = List.copyOf(services);
        products = products == null ? List.of() : List.copyOf(products);
        customerParts = customerParts == null ? List.of() : List.copyOf(customerParts);
    }

    public static WorkOrderDraft empty() {
        return new WorkOrderDraft(null, null, null, "", "", "", null, "", List.of(), "NEW");
    }

    public record ServiceLine(Long serviceId, Long discountId, BigDecimal priceWithDiscount,
                              String serviceNameSnapshot, BigDecimal priceWithoutDiscount,
                              String discountName, BigDecimal discountPercentage) { }
}
