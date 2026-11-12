package org.bike.trello.model;

import java.util.List;

public final class TrelloData {
    private TrelloData() { }
    public record Board(String id, String name) { }
    public record BoardList(String id, String name, String idBoard) { }
    public record Label(String id, String name, String color) { }
    public record Card(String id, String idBoard, String idList, List<String> idLabels) {
        public Card { idLabels = idLabels == null ? List.of() : List.copyOf(idLabels); }
    }
    public record Link(String cardId, String url, String status) { }
    public record Selection(String boardId, String listId, List<String> labelIds) {
        public Selection { labelIds = List.copyOf(labelIds); }
    }
}
