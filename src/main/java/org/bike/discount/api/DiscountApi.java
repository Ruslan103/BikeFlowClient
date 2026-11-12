package org.bike.discount.api;

import java.util.List;
import java.util.function.Consumer;
import org.bike.discount.api.dto.DiscountData;
import org.bike.discount.api.dto.DiscountBatchData;
import org.bike.discount.api.dto.DiscountRequest;

/** Asynchronous transport; callbacks run on the configured UI thread. */
public interface DiscountApi {
    void list(Consumer<List<DiscountData>> success, Consumer<Exception> failure);
    void search(String query, Consumer<List<DiscountData>> success, Consumer<Exception> failure);
    void listBatch(int page, int size, Consumer<DiscountBatchData> success, Consumer<Exception> failure);
    void create(DiscountRequest request, Consumer<DiscountData> success, Consumer<Exception> failure);
    void update(Long id, DiscountRequest request, Consumer<DiscountData> success, Consumer<Exception> failure);
    void delete(Long id, Runnable success, Consumer<Exception> failure);
}
