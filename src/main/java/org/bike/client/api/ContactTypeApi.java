package org.bike.client.api;

import java.util.List;
import java.util.function.Consumer;
import org.bike.client.api.dto.ContactTypeData;

@FunctionalInterface
public interface ContactTypeApi {
    void list(Consumer<List<ContactTypeData>> success, Consumer<Exception> failure);
}
