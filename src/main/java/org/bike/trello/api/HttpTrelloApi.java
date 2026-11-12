package org.bike.trello.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.bike.api.ApiClient;
import org.bike.trello.model.TrelloData.Board;
import org.bike.trello.model.TrelloData.BoardList;
import org.bike.trello.model.TrelloData.Label;
import org.bike.trello.model.TrelloData.Card;
import org.bike.trello.model.TrelloData.Selection;
import org.bike.trello.model.TrelloData.Link;

public final class HttpTrelloApi implements TrelloApi {
    private static final ObjectMapper JSON = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final ApiClient api;
    public HttpTrelloApi(ApiClient api) { this.api = Objects.requireNonNull(api); }
    @Override public void boards(Consumer<List<Board>> success, Consumer<Exception> failure) {
        request("GET", "/api/trello/boards", null, list(Board.class), success, failure);
    }
    @Override public void lists(String id, Consumer<List<BoardList>> success, Consumer<Exception> failure) {
        request("GET", "/api/trello/boards/" + encode(id) + "/lists", null, list(BoardList.class), success, failure);
    }
    @Override public void labels(String id, Consumer<List<Label>> success, Consumer<Exception> failure) {
        request("GET", "/api/trello/boards/" + encode(id) + "/labels", null, list(Label.class), success, failure);
    }
    @Override public void card(String number, Consumer<Card> success, Consumer<Exception> failure) {
        request("GET", path(number), null, JSON.constructType(Card.class), success, failure);
    }
    @Override public void synchronize(String number, Selection selection, Consumer<Link> success, Consumer<Exception> failure) {
        request("POST", path(number) + "/card", Map.of("listId", selection.listId(), "labelIds", selection.labelIds()),
                JSON.constructType(Link.class), success, failure);
    }
    private String path(String number) { return "/api/work-orders/" + encode(number) + "/trello"; }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private JavaType list(Class<?> type) { return JSON.getTypeFactory().constructCollectionType(List.class, type); }
    private <T> void request(String method, String path, Object body, JavaType type, Consumer<T> success, Consumer<Exception> failure) {
        api.request(method, path, body == null ? null : JSON.valueToTree(body), response -> {
            T value;
            try {
                value = JSON.readValue(response.body(), type);
                if (value == null) throw new IOException("Пустой ответ.");
            } catch (IOException exception) {
                failure.accept(new IOException("Не удалось прочитать данные Trello.", exception));
                return;
            }
            success.accept(value);
        }, failure);
    }
}
