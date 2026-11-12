package org.bike.workorder.model;

public enum ProductSupplyStatus {
    IN_STOCK("В наличии"), TO_ORDER("Нужно заказать"), ORDERED("Заказан"), RECEIVED("Получен");

    private final String label;

    ProductSupplyStatus(String label) { this.label = label; }

    @Override
    public String toString() { return label; }
}
