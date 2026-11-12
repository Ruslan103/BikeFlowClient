package org.bike.trello.api;

import java.util.List;
import java.util.function.Consumer;
import org.bike.trello.model.TrelloData.Board;
import org.bike.trello.model.TrelloData.BoardList;
import org.bike.trello.model.TrelloData.Label;
import org.bike.trello.model.TrelloData.Card;
import org.bike.trello.model.TrelloData.Selection;
import org.bike.trello.model.TrelloData.Link;

public interface TrelloApi {
    void boards(Consumer<List<Board>> success, Consumer<Exception> failure);
    void lists(String boardId, Consumer<List<BoardList>> success, Consumer<Exception> failure);
    void labels(String boardId, Consumer<List<Label>> success, Consumer<Exception> failure);
    void card(String number, Consumer<Card> success, Consumer<Exception> failure);
    void synchronize(String number, Selection selection, Consumer<Link> success, Consumer<Exception> failure);
}
