package org.bike.client.api.dto;

import java.util.List;

public record ClientBatchData(
        List<ClientData> clients,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {
    public ClientBatchData {
        clients = clients == null ? List.of() : List.copyOf(clients);
    }
}
