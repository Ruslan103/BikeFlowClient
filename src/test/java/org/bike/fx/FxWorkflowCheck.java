package org.bike.fx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import org.bike.client.api.dto.*;
import org.bike.fx.client.ClientEditorController;
import org.bike.fx.workorder.WorkOrderController;
import org.bike.profile.ServerProfile;
import org.bike.workorder.model.ClientSnapshotData;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Real FXML + HTTP adapter against an isolated fixture server. No persistent data is written. */
public final class FxWorkflowCheck {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    private static final AtomicReference<JsonNode> orderRequest = new AtomicReference<>();
    private static final AtomicBoolean stale = new AtomicBoolean(true);
    private static final ClientData client =
            new ClientData(
                    1L,
                    100L,
                    "Иванов",
                    "Иван",
                    null,
                    null,
                    List.of(new ClientBicycleData(10L, 1L, "Cube", "Красный", 0L)),
                    List.of(
                            new ClientContactData(
                                    20L, 1L, "PHONE", "Телефон", "79991234567", null)),
                    "PERSON",
                    null,
                    null,
                    0L);
    private static final ClientSnapshotData snapshot =
            new ClientSnapshotData(
                    "PERSON",
                    "Иванов Иван",
                    "Иван",
                    "Иванов",
                    null,
                    null,
                    null,
                    "PHONE",
                    "79991234567",
                    "Cube",
                    "Красный",
                    null);

    public static void main(String[] args) throws Exception {
        var startup = new CompletableFuture<Void>();
        Platform.startup(() -> startup.complete(null));
        startup.get(10, TimeUnit.SECONDS);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    try {
                        String path = exchange.getRequestURI().getPath();
                        String body =
                                new String(
                                        exchange.getRequestBody().readAllBytes(),
                                        StandardCharsets.UTF_8);
                        int code = 200;
                        Object response;
                        switch (path) {
                            case "/api/auth/csrf" ->
                                    response =
                                            java.util.Map.of(
                                                    "headerName",
                                                    "X-CSRF-TOKEN",
                                                    "token",
                                                    "fixture");
                            case "/api/auth/login" ->
                                    response = java.util.Map.of("roleCodes", List.of("ADMIN"));
                            case "/api/trello/boards" -> response = List.of();
                            case "/api/contact-types" ->
                                    response = List.of(new ContactTypeData(1L, "PHONE", "Телефон"));
                            case "/api/work-orders/client-selection" ->
                                    response =
                                            java.util.Map.of(
                                                    "snapshot",
                                                    snapshot,
                                                    "expectedClientVersion",
                                                    0,
                                                    "expectedBicycleVersion",
                                                    0);
                            case "/api/clients/1" -> {
                                if (stale.getAndSet(false)) {
                                    code = 409;
                                    response =
                                            java.util.Map.of(
                                                    "detail",
                                                    "Карточка изменена. Перечитайте данные.",
                                                    "code",
                                                    "STALE_CARD");
                                } else response = client;
                            }
                            case "/api/work-orders" -> {
                                JsonNode request = JSON.readTree(body);
                                orderRequest.set(request);
                                require(
                                        !request.has("details")
                                                && !request.has("client")
                                                && !request.has("snapshot"),
                                        "Blank must only send references");
                                require(
                                        request.path("clientId").asLong() == 1
                                                && request.path("clientBicycleId").asLong() == 10,
                                        "Client/bicycle references");
                                require(
                                        "REFRESH".equals(request.path("snapshotAction").asText()),
                                        "Explicit snapshot action");
                                var result =
                                        (com.fasterxml.jackson.databind.node.ObjectNode)
                                                request.deepCopy();
                                result.put("blankNumber", "FIXTURE-01");
                                result.put("createdAt", "2026-11-07T10:00:00Z");
                                result.set("client", JSON.valueToTree(client));
                                result.set("snapshot", JSON.valueToTree(snapshot));
                                response = result;
                            }
                            default ->
                                    throw new AssertionError("Unexpected fixture request: " + path);
                        }
                        byte[] bytes = JSON.writeValueAsBytes(response);
                        exchange.getResponseHeaders()
                                .set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(code, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    } catch (Throwable error) {
                        serverFailure.set(error);
                        exchange.sendResponseHeaders(500, -1);
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
        FxContext context = fx(FxContext::new);
        try {
            var login = new CompletableFuture<Void>();
            fx(
                    () -> {
                        context.connection.login(
                                new ServerProfile(
                                        "fixture",
                                        "Fixture",
                                        "http://127.0.0.1:" + server.getAddress().getPort(),
                                        "fixture",
                                        Set.of(),
                                        false,
                                        false),
                                new char[] {'x'},
                                value -> login.complete(null),
                                login::completeExceptionally);
                        return null;
                    });
            login.get(10, TimeUnit.SECONDS);
            WorkOrderController order =
                    fx(
                            () -> {
                                FxView.Loaded<WorkOrderController> view = FxView.load("WorkOrder");
                                FxView.scene(view.root());
                                view.controller().bind(context);
                                return view.controller();
                            });
            fx(
                    () -> {
                        invoke(order, "select", ClientData.class, client);
                        return null;
                    });
            await(() -> fx(() -> (boolean) field(order, "selectionReady")));
            fx(
                    () -> {
                        ((ComboBox<ClientBicycleData>) field(order, "bicycle"))
                                .setValue(client.bicycles().getFirst());
                        ((ComboBox<ClientContactData>) field(order, "contact"))
                                .setValue(client.contacts().getFirst());
                        ((TextField) field(order, "number")).setText("FIXTURE-01");
                        return null;
                    });
            await(() -> fx(() -> (boolean) field(order, "selectionReady")));
            fx(
                    () -> {
                        invoke(order, "save");
                        return null;
                    });
            await(
                    () ->
                            fx(
                                    () ->
                                            "FIXTURE-01".equals(field(order, "originalNumber"))
                                                    && !(boolean) field(order, "busy")));
            require(orderRequest.get() != null, "Blank was saved");
            require(
                    fx(() -> ((Label) field(order, "clientName")).getText()).equals("Иванов Иван"),
                    "Saved snapshot is rendered");

            ClientEditorController editor =
                    fx(
                            () -> {
                                FxView.Loaded<ClientEditorController> view =
                                        FxView.load("ClientEditor");
                                FxView.scene(view.root());
                                view.controller().bind(context, client, value -> {});
                                invoke(view.controller(), "edit");
                                ((TextField) field(view.controller(), "firstName"))
                                        .setText("Несохранённый ввод");
                                invoke(view.controller(), "save");
                                return view.controller();
                            });
            await(() -> fx(() -> !(boolean) field(editor, "busy")));
            require(
                    fx(() -> ((TextField) field(editor, "firstName")).getText())
                            .equals("Несохранённый ввод"),
                    "409 must preserve user input");
            require(
                    fx(() -> ((Label) field(editor, "status")).getText()).contains("Перечитайте"),
                    "409 explains recovery");
            fx(
                    () -> {
                        order.dispose();
                        return null;
                    });
            if (serverFailure.get() != null) throw new AssertionError(serverFailure.get());
            System.out.println(
                    "JavaFX workflow passed: authentication, selection preview, snapshot-only blank"
                        + " save, 409 input retention");
        } finally {
            fx(
                    () -> {
                        context.close();
                        return null;
                    });
            server.stop(0);
            Platform.exit();
        }
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void invoke(Object target, String name, Class<?> type, Object value)
            throws Exception {
        var method = target.getClass().getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(target, value);
    }

    private static <T> T fx(java.util.concurrent.Callable<T> task) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(
                () -> {
                    try {
                        result.complete(task.call());
                    } catch (Throwable error) {
                        result.completeExceptionally(error);
                    }
                });
        return result.get(10, TimeUnit.SECONDS);
    }

    private static void await(java.util.concurrent.Callable<Boolean> ready) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!ready.call()) {
            if (System.nanoTime() > deadline)
                throw new AssertionError("Timed out waiting for JavaFX response");
            Thread.sleep(20);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
