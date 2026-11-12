package org.bike.client.api;

import java.util.List;
import java.util.function.Consumer;
import org.bike.client.api.dto.ClientData;

/** Callbacks run on the configured UI thread. */
public interface ClientSearchApi {
    void search(String phone, Consumer<List<ClientData>> success, Consumer<Exception> failure);
}
