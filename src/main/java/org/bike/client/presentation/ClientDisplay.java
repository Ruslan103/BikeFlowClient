package org.bike.client.presentation;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bike.client.api.dto.ClientBicycleData;
import org.bike.client.api.dto.ClientContactData;
import org.bike.client.api.dto.ClientData;

public final class ClientDisplay {
    private ClientDisplay() { }

    public static String name(ClientData client) {
        if ("ORGANIZATION".equals(client.type())) return text(client.organizationName());
        String name = Stream.of(client.lastName(), client.firstName(), client.middleName())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
        return name.isBlank()
                ? (client.contacts().isEmpty() ? "Клиент № " + client.publicId() : client.contacts().getFirst().value())
                : name;
    }

    public static String bicycle(ClientBicycleData bicycle) {
        return text(bicycle.brandName())
                + (bicycle.comment() == null || bicycle.comment().isBlank() ? "" : " · " + bicycle.comment())
                + " [" + bicycle.id() + "]";
    }

    public static String contact(ClientContactData contact) {
        return text(contact.typeName()) + ": " + text(contact.value())
                + (contact.label() == null || contact.label().isBlank() ? "" : " · " + contact.label());
    }

    public static String details(ClientData client) {
        StringBuilder result = new StringBuilder(name(client)).append("\nID: ").append(client.id())
                .append("\nТип: ").append("ORGANIZATION".equals(client.type()) ? "Организация" : "Физическое лицо")
                .append("\nПредставитель: ").append(text(client.representative()))
                .append("\nНомер клиента: ").append(text(client.publicId()))
                .append("\n\nКомментарий клиента:\n").append(text(client.comment()))
                .append("\n\nВелосипеды:\n");
        if (client.bicycles().isEmpty()) result.append("Нет\n");
        client.bicycles().forEach(bicycle -> result.append(bicycle(bicycle)).append('\n'));
        result.append("\nКонтакты:\n");
        if (client.contacts().isEmpty()) result.append("Нет\n");
        client.contacts().forEach(contact -> result.append(contact(contact)).append('\n'));
        return result.toString();
    }

    private static String text(Object value) {
        return Objects.toString(value, "—");
    }
}
