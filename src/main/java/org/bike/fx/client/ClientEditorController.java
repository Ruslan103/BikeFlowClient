package org.bike.fx.client;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.bike.client.api.dto.*;
import org.bike.client.presentation.ClientDisplay;
import org.bike.fx.*;

import java.util.*;
import java.util.function.Consumer;

public final class ClientEditorController {
    @FXML private VBox root;
    @FXML private ComboBox<String> type;
    @FXML private VBox personFields, organizationFields, form;
    @FXML
    private TextField firstName,
            lastName,
            middleName,
            organization,
            representative,
            contactValue,
            contactLabel;
    @FXML private TextArea comment;
    @FXML private TextField bicycleBrand, bicycleComment;
    @FXML private ListView<ClientContactData> contacts;
    @FXML private ListView<ClientBicycleData> bicycles;
    @FXML private ComboBox<ContactTypeData> contactType;
    @FXML
    private Button
            editButton,
            saveButton,
            addContactButton,
            editContactButton,
            deleteContactButton,
            addBicycleButton,
            editBicycleButton,
            deleteBicycleButton;
    @FXML private Label status;
    private SearchableComboBox<String> typeOptions;
    private SearchableComboBox<ContactTypeData> contactTypeOptions;
    private final ContextMenu contactContextMenu = new ContextMenu();
    private final MenuItem addContactMenuItem = new MenuItem("Добавить");
    private final MenuItem editContactMenuItem = new MenuItem("Редактировать");
    private final MenuItem deleteContactMenuItem = new MenuItem("Удалить");
    private final ContextMenu bicycleContextMenu = new ContextMenu();
    private final MenuItem addBicycleMenuItem = new MenuItem("Добавить");
    private final MenuItem editBicycleMenuItem = new MenuItem("Редактировать");
    private final MenuItem deleteBicycleMenuItem = new MenuItem("Удалить");
    private FxContext context;
    private ClientData client;
    private Consumer<ClientData> saved;
    private boolean editing, busy;
    private int contactEditIndex = -1;
    private Long contactToEditId;
    private boolean allowDuplicateContacts;
    private int bicycleEditIndex = -1;
    private enum BicycleOperation { CREATE, UPDATE, DELETE }
    private final Map<ClientBicycleData, BicycleOperation> pendingBicycles = new IdentityHashMap<>();

    @FXML
    private void initialize() {
        StatusMessages.install(status);
        typeOptions = SearchableComboBox.install(type, value -> value);
        contactTypeOptions = SearchableComboBox.install(contactType, ContactTypeData::name);
        typeOptions.setItems(List.of("Физическое лицо", "Организация"));
        type.getSelectionModel().selectFirst();
        type.valueProperty().addListener((o, a, b) -> showType());
        contacts.setCellFactory(
                list ->
                        new ListCell<>() {
                            protected void updateItem(ClientContactData value, boolean empty) {
                                super.updateItem(value, empty);
                                boolean pending = !empty && value != null && value.id() == null;
                                setText(
                                        empty || value == null
                                                ? null
                                                : ClientDisplay.contact(value)
                                                        + (pending ? " · не сохранён" : ""));
                                setStyle(pending ? "-fx-text-fill: #a66500;" : null);
                            }
                        });
        bicycles.setCellFactory(
                list ->
                        new ListCell<>() {
                            protected void updateItem(ClientBicycleData value, boolean empty) {
                                super.updateItem(value, empty);
                                boolean pending = !empty && value != null && pendingBicycles.containsKey(value);
                                setText(empty || value == null ? null : bicycleText(value));
                                setStyle(pending ? "-fx-text-fill: #a66500;" : null);
                            }
                        });
        contacts.getSelectionModel()
                .selectedItemProperty()
                .addListener((o, a, b) -> actions());
        contactType.valueProperty().addListener((o, a, b) -> updateContactValueValidation());
        contactContextMenu.getItems().setAll(
                addContactMenuItem, editContactMenuItem, deleteContactMenuItem);
        addContactMenuItem.setOnAction(event -> addContact());
        editContactMenuItem.setOnAction(event -> editContact());
        deleteContactMenuItem.setOnAction(event -> deleteContact());
        contacts.setContextMenu(contactContextMenu);
        contacts.addEventHandler(
                ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::prepareContactContextMenu);
        bicycleContextMenu.getItems().setAll(
                addBicycleMenuItem, editBicycleMenuItem, deleteBicycleMenuItem);
        addBicycleMenuItem.setOnAction(event -> addBicycle());
        editBicycleMenuItem.setOnAction(event -> editBicycle());
        deleteBicycleMenuItem.setOnAction(event -> deleteBicycle());
        bicycles.setContextMenu(bicycleContextMenu);
        bicycles.addEventHandler(
                ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::prepareBicycleContextMenu);
        bicycles.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> actions());
        contactValue.textProperty().addListener((o, a, b) -> actions());
        contactValue.setOnAction(event -> addContact());
        contactLabel.setOnAction(event -> addContact());
        bicycleBrand.textProperty().addListener((o, a, b) -> actions());
        bicycleComment.textProperty().addListener((o, a, b) -> actions());
        showType();
    }

    public void bind(FxContext context, ClientData value, Consumer<ClientData> saved) {
        bind(context, value, saved, null);
    }

    public void bind(
            FxContext context,
            ClientData value,
            Consumer<ClientData> saved,
            String nameInput) {
        this.context = context;
        this.client = value;
        this.saved = saved;
        display();
        if (client == null) {
            prefillPersonName(nameInput);
            prefillPhoneContact(nameInput);
        }
        context.clients.types(
                values -> {
                    contactTypeOptions.setItems(values);
                    selectPhoneContactType();
                    if (contactToEditId != null) editContactById(contactToEditId);
                },
                this::error);
    }

    private void prefillPersonName(String input) {
        if (input == null || input.isBlank()) return;
        var nameParts =
                java.util.Arrays.stream(input.trim().split("[\\s,;]+"))
                        .filter(part -> part.codePoints().anyMatch(Character::isLetter))
                        .limit(3)
                        .toList();
        if (nameParts.isEmpty()) return;
        firstName.setText(nameParts.get(0));
        if (nameParts.size() > 1) lastName.setText(nameParts.get(1));
        if (nameParts.size() > 2) middleName.setText(nameParts.get(2));
    }

    private void prefillPhoneContact(String input) {
        if (input == null || input.isBlank()) return;
        String digits = digitsOnly(input);
        boolean containsLetters = input.codePoints().anyMatch(Character::isLetter);
        if (digits.length() >= 10 && digits.length() <= 15 && !containsLetters)
            contactValue.setText(digits);
    }

    private void selectPhoneContactType() {
        contactTypeOptions.items().stream()
                .filter(this::isPhoneContactType)
                .findFirst()
                .ifPresent(contactType::setValue);
    }

    private boolean isPhoneContactType(ContactTypeData value) {
        return value != null
                && ((value.code() != null
                                && value.code()
                                        .toLowerCase(java.util.Locale.ROOT)
                                        .contains("phone"))
                        || (value.name() != null
                                && value.name()
                                        .toLowerCase(java.util.Locale.ROOT)
                                        .contains("телефон")));
    }

    private void updateContactValueValidation() {
        if (isPhoneContactType(contactType.getValue())) {
            contactValue.setPromptText("Только цифры");
            String digits = digitsOnly(contactValue.getText());
            if (!Objects.equals(digits, contactValue.getText())) contactValue.setText(digits);
            if (contactValue.getTextFormatter() == null)
                contactValue.setTextFormatter(
                        new TextFormatter<>(
                                change ->
                                        change.getControlNewText().matches("[0-9]*")
                                                ? change
                                                : null));
        } else {
            contactValue.setPromptText("");
            contactValue.setTextFormatter(null);
        }
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private void showType() {
        boolean person = type.getSelectionModel().getSelectedIndex() != 1;
        personFields.setVisible(person);
        personFields.setManaged(person);
        organizationFields.setVisible(!person);
        organizationFields.setManaged(!person);
    }

    private void display() {
        editing = client == null;
        type.getSelectionModel()
                .select(client != null && "ORGANIZATION".equals(client.type()) ? 1 : 0);
        firstName.setText(client == null ? "" : client.firstName());
        lastName.setText(client == null ? "" : client.lastName());
        middleName.setText(client == null ? "" : client.middleName());
        organization.setText(client == null ? "" : client.organizationName());
        representative.setText(client == null ? "" : client.representative());
        comment.setText(client == null ? "" : client.comment());
        contacts.getItems().setAll(client == null ? List.of() : client.contacts());
        contactEditIndex = -1;
        bicycleEditIndex = -1;
        bicycleBrand.clear();
        bicycleComment.clear();
        pendingBicycles.clear();
        bicycles.getItems().setAll(client == null ? List.of() : client.bicycles());
        actions();
    }

    private String bicycleText(ClientBicycleData bicycle) {
        BicycleOperation operation = pendingBicycles.get(bicycle);
        if (operation == null) return ClientDisplay.bicycle(bicycle);
        String comment =
                bicycle.comment() == null || bicycle.comment().isBlank()
                        ? ""
                        : " · " + bicycle.comment();
        String state = operation == BicycleOperation.DELETE ? " · будет удалён" : " · не сохранён";
        return Objects.toString(bicycle.brandName(), "Велосипед") + comment + state;
    }

    private void actions() {
        root.setDisable(busy);
        form.setDisable(!editing || busy);
        comment.setDisable(!editing || busy);
        contacts.setDisable(busy || contactEditIndex >= 0);
        bicycles.setDisable(busy || bicycleEditIndex >= 0);
        type.setDisable(client != null || busy);
        contactType.setDisable(!editing || busy);
        contactValue.setDisable(!editing || busy);
        contactLabel.setDisable(!editing || busy);
        bicycleBrand.setDisable(!editing || busy);
        bicycleComment.setDisable(!editing || busy);
        addContactButton.setText(contactEditIndex >= 0 ? "Сохранить" : "Добавить");
        addContactButton.setDisable(
                !editing || busy || contactValue.getText() == null || contactValue.getText().isBlank());
        var selectedContact = contacts.getSelectionModel().getSelectedItem();
        editContactButton.setText(contactEditIndex >= 0 ? "Отменить" : "Редактировать");
        editContactButton.setDisable(busy || (contactEditIndex < 0 && selectedContact == null));
        deleteContactButton.setDisable(
                busy || contactEditIndex >= 0 || selectedContact == null);
        addContactMenuItem.setText(contactEditIndex >= 0 ? "Сохранить" : "Добавить");
        addContactMenuItem.setDisable(
                !editing || busy || contactValue.getText() == null || contactValue.getText().isBlank());
        editContactMenuItem.setText(contactEditIndex >= 0 ? "Отменить" : "Редактировать");
        editContactMenuItem.setDisable(busy || (contactEditIndex < 0 && selectedContact == null));
        deleteContactMenuItem.setDisable(
                busy || contactEditIndex >= 0 || selectedContact == null);
        editButton.setDisable(editing || busy);
        saveButton.setDisable(!editing || busy);
        addBicycleButton.setText(bicycleEditIndex >= 0 ? "Сохранить" : "Добавить");
        addBicycleButton.setDisable(
                !editing || busy || bicycleBrand.getText() == null || bicycleBrand.getText().isBlank());
        var selectedBicycle = bicycles.getSelectionModel().getSelectedItem();
        editBicycleButton.setText(bicycleEditIndex >= 0 ? "Отменить" : "Редактировать");
        editBicycleButton.setDisable(busy || (bicycleEditIndex < 0 && selectedBicycle == null));
        deleteBicycleButton.setText(
                selectedBicycle != null
                                && pendingBicycles.get(selectedBicycle) == BicycleOperation.DELETE
                        ? "Отменить удаление"
                        : "Удалить");
        deleteBicycleButton.setDisable(busy || selectedBicycle == null);
        addBicycleMenuItem.setText(bicycleEditIndex >= 0 ? "Сохранить" : "Добавить");
        addBicycleMenuItem.setDisable(
                !editing || busy || bicycleBrand.getText() == null || bicycleBrand.getText().isBlank());
        editBicycleMenuItem.setText(bicycleEditIndex >= 0 ? "Отменить" : "Редактировать");
        editBicycleMenuItem.setDisable(busy || (bicycleEditIndex < 0 && selectedBicycle == null));
        deleteBicycleMenuItem.setText(
                selectedBicycle != null
                                && pendingBicycles.get(selectedBicycle) == BicycleOperation.DELETE
                        ? "Отменить удаление"
                        : "Удалить");
        deleteBicycleMenuItem.setDisable(busy || selectedBicycle == null);
    }

    @FXML
    private void edit() {
        startEditing();
    }

    public void startEditing() {
        editing = true;
        actions();
    }

    public void startAddingContactAllowDuplicates() {
        editing = true;
        allowDuplicateContacts = true;
        contactEditIndex = -1;
        contacts.getSelectionModel().clearSelection();
        contactValue.clear();
        contactLabel.clear();
        selectPhoneContactType();
        actions();
        contactValue.requestFocus();
    }

    public void startEditingContact(Long contactId) {
        editing = true;
        contactToEditId = contactId;
        if (!contactTypeOptions.items().isEmpty()) editContactById(contactId);
        else actions();
    }

    public void startAddingBicycle() {
        editing = true;
        bicycleEditIndex = -1;
        bicycles.getSelectionModel().clearSelection();
        clearBicycleEditor();
        actions();
        bicycleBrand.requestFocus();
    }

    public void startEditingBicycle(Long bicycleId) {
        editing = true;
        for (int index = 0; index < bicycles.getItems().size(); index++) {
            if (Objects.equals(bicycles.getItems().get(index).id(), bicycleId)) {
                bicycles.getSelectionModel().select(index);
                editBicycle();
                return;
            }
        }
        actions();
    }

    private void editContactById(Long contactId) {
        for (int index = 0; index < contacts.getItems().size(); index++) {
            if (Objects.equals(contacts.getItems().get(index).id(), contactId)) {
                contacts.getSelectionModel().select(index);
                editContact();
                contactToEditId = null;
                return;
            }
        }
        contactToEditId = null;
    }

    @FXML
    private void addContact() {
        addContactIfValid();
    }

    private boolean addContactIfValid() {
        if (contactValue.getText() == null || contactValue.getText().isBlank()) {
            StatusMessages.error(status, "Введите контакт.");
            return false;
        }
        var t = contactType.getValue();
        if (t == null) {
            StatusMessages.error(status, "Выберите тип контакта.");
            return false;
        }
        if (isPhoneContactType(t) && !contactValue.getText().matches("[0-9]+")) {
            StatusMessages.error(status, "Телефон должен содержать только цифры.");
            return false;
        }
        ClientContactData selected =
                contactEditIndex < 0 ? null : contacts.getItems().get(contactEditIndex);
        var item =
                new ClientContactData(
                        selected == null ? null : selected.id(),
                        t.id(),
                        t.code(),
                        t.name(),
                        contactValue.getText(),
                        contactLabel.getText());
        if (contactEditIndex < 0) contacts.getItems().add(item);
        else contacts.getItems().set(contactEditIndex, item);
        contactEditIndex = -1;
        contacts.getSelectionModel().clearSelection();
        contactValue.clear();
        contactLabel.clear();
        selectPhoneContactType();
        actions();
        return true;
    }

    @FXML
    private void addBicycle() {
        addBicycleIfValid();
    }

    private boolean addBicycleIfValid() {
        if (bicycleBrand.getText() == null || bicycleBrand.getText().isBlank()) {
            StatusMessages.error(status, "Укажите марку велосипеда.");
            return false;
        }
        ClientBicycleData selected =
                bicycleEditIndex < 0 ? null : bicycles.getItems().get(bicycleEditIndex);
        var value =
                new ClientBicycleData(
                        selected == null ? null : selected.id(),
                        selected == null ? null : selected.brandId(),
                        bicycleBrand.getText().strip(),
                        bicycleComment.getText(),
                        selected == null ? null : selected.version());
        BicycleOperation operation = selected == null ? BicycleOperation.CREATE : pendingBicycles.get(selected);
        if (operation == null || operation == BicycleOperation.DELETE)
            operation = selected == null || selected.id() == null
                    ? BicycleOperation.CREATE
                    : BicycleOperation.UPDATE;
        if (selected == null) bicycles.getItems().add(value);
        else {
            bicycles.getItems().set(bicycleEditIndex, value);
            pendingBicycles.remove(selected);
        }
        pendingBicycles.put(value, operation);
        bicycleEditIndex = -1;
        bicycles.getSelectionModel().clearSelection();
        clearBicycleEditor();
        bicycles.refresh();
        actions();
        status.setText("Велосипед будет сохранён вместе с карточкой клиента.");
        return true;
    }

    @FXML
    private void editBicycle() {
        if (bicycleEditIndex >= 0) {
            bicycleEditIndex = -1;
            clearBicycleEditor();
            actions();
            return;
        }
        int selectedIndex = bicycles.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) return;
        ClientBicycleData selected = bicycles.getItems().get(selectedIndex);
        editing = true;
        bicycleEditIndex = selectedIndex;
        if (pendingBicycles.get(selected) == BicycleOperation.DELETE)
            pendingBicycles.put(
                    selected,
                    selected.id() == null
                            ? BicycleOperation.CREATE
                            : BicycleOperation.UPDATE);
        bicycleBrand.setText(selected.brandName());
        bicycleComment.setText(selected.comment());
        actions();
    }

    @FXML
    private void deleteBicycle() {
        int selectedIndex = bicycles.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) return;
        ClientBicycleData selected = bicycles.getItems().get(selectedIndex);
        editing = true;
        BicycleOperation operation = pendingBicycles.get(selected);
        if (operation == BicycleOperation.DELETE) {
            pendingBicycles.put(
                    selected,
                    selected.id() == null
                            ? BicycleOperation.CREATE
                            : BicycleOperation.UPDATE);
            status.setText("Удаление отменено. Изменения применятся при сохранении карточки.");
        } else {
            var confirmation =
                    FxView.confirmation(
                            "Удалить выбранный велосипед из карточки?",
                            ButtonType.YES,
                            ButtonType.NO);
            confirmation.initOwner(bicycles.getScene().getWindow());
            if (confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
            if (selected.id() == null) {
                pendingBicycles.remove(selected);
                bicycles.getItems().remove(selectedIndex);
                status.setText("Новый велосипед удалён из карточки.");
            } else {
                pendingBicycles.put(selected, BicycleOperation.DELETE);
                status.setText("Велосипед будет удалён после сохранения карточки.");
            }
        }
        bicycleEditIndex = -1;
        bicycles.getSelectionModel().clearSelection();
        clearBicycleEditor();
        bicycles.refresh();
        actions();
    }

    private void clearBicycleEditor() {
        bicycleBrand.clear();
        bicycleComment.clear();
    }

    @FXML
    private void editContact() {
        if (contactEditIndex >= 0) {
            contactEditIndex = -1;
            contactValue.clear();
            contactLabel.clear();
            selectPhoneContactType();
            actions();
            return;
        }
        int selectedIndex = contacts.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) return;
        ClientContactData selected = contacts.getItems().get(selectedIndex);
        editing = true;
        contactEditIndex = selectedIndex;
        contactTypeOptions.items().stream()
                .filter(value -> value.id().equals(selected.typeId()))
                .findFirst()
                .ifPresent(contactType::setValue);
        contactValue.setText(
                isPhoneContactType(contactType.getValue())
                        ? digitsOnly(selected.value())
                        : selected.value());
        contactLabel.setText(selected.label());
        actions();
    }

    @FXML
    private void deleteContact() {
        int selectedIndex = contacts.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) return;
        var confirmation =
                FxView.confirmation(
                        "Удалить выбранный контакт из карточки?",
                        ButtonType.YES,
                        ButtonType.NO);
        confirmation.initOwner(contacts.getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        editing = true;
        contacts.getItems().remove(selectedIndex);
        contacts.getSelectionModel().clearSelection();
        contactValue.clear();
        contactLabel.clear();
        selectPhoneContactType();
        actions();
        status.setText("Контакт удалён из карточки. Нажмите «Сохранить карточку», чтобы применить изменения.");
    }

    private void prepareContactContextMenu(ContextMenuEvent event) {
        Node target = event.getPickResult().getIntersectedNode();
        while (target != null && !(target instanceof ListCell<?>)) target = target.getParent();
        if (target instanceof ListCell<?> cell) {
            if (cell.isEmpty()) contacts.getSelectionModel().clearSelection();
            else contacts.getSelectionModel().select(cell.getIndex());
        }
        actions();
    }

    private void prepareBicycleContextMenu(ContextMenuEvent event) {
        Node target = event.getPickResult().getIntersectedNode();
        while (target != null && !(target instanceof ListCell<?>)) target = target.getParent();
        if (target instanceof ListCell<?> cell) {
            if (cell.isEmpty()) bicycles.getSelectionModel().clearSelection();
            else bicycles.getSelectionModel().select(cell.getIndex());
        }
        actions();
    }

    @FXML
    private void save() {
        save(false);
    }

    private void save(boolean allowDuplicate) {
        if (busy || context == null) return;
        if (!contactValue.getText().isBlank()) {
            if (contactType.getValue() == null) {
                StatusMessages.error(status, "Выберите тип введённого контакта.");
                return;
            }
            if (!addContactIfValid()) return;
        }
        if (bicycleEditIndex >= 0 || !bicycleBrand.getText().isBlank()) {
            if (!addBicycleIfValid()) return;
        }
        if (!validatePhoneContacts()) return;
        var request =
                new ClientCardRequest(
                        type.getSelectionModel().getSelectedIndex() == 1
                                ? "ORGANIZATION"
                                : "PERSON",
                        client == null ? null : client.version(),
                        firstName.getText(),
                        lastName.getText(),
                        middleName.getText(),
                        organization.getText(),
                        representative.getText(),
                        comment.getText(),
                        contacts.getItems().stream()
                                .map(
                                        c ->
                                                new ClientCardRequest.ContactInput(
                                                        c.id(), c.typeId(), c.value(), c.label()))
                                .toList(),
                        allowDuplicate || allowDuplicateContacts);
        busy = true;
        actions();
        Consumer<ClientData> success =
                value -> {
                    client = value;
                    savePendingBicycles(pendingBicyclesInDisplayOrder(), 0);
                };
        Consumer<Exception> failure =
                error -> {
                    busy = false;
                    actions();
                    error(error);
                    if (error instanceof org.bike.api.ApiException api
                            && "DUPLICATE_CONTACT".equals(api.code())) {
                        var existing = new ButtonType("Выбрать существующего");
                        var separate = new ButtonType("Создать другого");
                        var dialog =
                                FxView.confirmation(
                                        error.getMessage(),
                                        existing,
                                        separate,
                                        ButtonType.CANCEL);
                        dialog.initOwner(form.getScene().getWindow());
                        var answer = dialog.showAndWait().orElse(ButtonType.CANCEL);
                        if (answer == separate) save(true);
                        else if (answer == existing) selectExisting(api.searchQuery());
                    }
                };
        if (client == null) context.clients.create(request, success, failure);
        else context.clients.update(client.id(), request, success, failure);
    }

    private boolean validatePhoneContacts() {
        for (ClientContactData contact : contacts.getItems()) {
            var contactType =
                    new ContactTypeData(contact.typeId(), contact.typeCode(), contact.typeName());
            if (isPhoneContactType(contactType)
                    && (contact.value() == null || !contact.value().matches("[0-9]+"))) {
                StatusMessages.error(status, "Телефон должен содержать только цифры.");
                contacts.getSelectionModel().select(contact);
                return false;
            }
        }
        return true;
    }

    private List<ClientBicycleData> pendingBicyclesInDisplayOrder() {
        return bicycles.getItems().stream().filter(pendingBicycles::containsKey).toList();
    }

    private void savePendingBicycles(List<ClientBicycleData> pending, int index) {
        if (index >= pending.size()) {
            busy = false;
            saved.accept(client);
            Window editorWindow = root.getScene().getWindow();
            Window owner = editorWindow instanceof Stage stage ? stage.getOwner() : editorWindow;
            editorWindow.hide();
            FxView.toast(owner, "Карточка клиента сохранена");
            return;
        }

        ClientBicycleData pendingBicycle = pending.get(index);
        BicycleOperation operation = pendingBicycles.get(pendingBicycle);
        ClientData beforeSave = client;
        var request =
                new BicycleCardRequest(
                        pendingBicycle.version(),
                        pendingBicycle.brandName(),
                        pendingBicycle.comment());
        Consumer<ClientData> success =
                updated -> {
                    if (operation == BicycleOperation.DELETE) {
                        client = updated;
                        removePendingBicycle(pendingBicycle);
                        savePendingBicycles(pending, index + 1);
                        return;
                    }
                    ClientBicycleData persistedBicycle =
                            findPersistedBicycle(pendingBicycle, beforeSave, updated);
                    client = updated;
                    if (persistedBicycle == null) {
                        busy = false;
                        actions();
                        StatusMessages.error(status,
                                "Клиент сохранён, но сервер не вернул сохранённый велосипед. "
                                        + "Проверьте список и повторите сохранение.");
                        return;
                    }
                    replacePendingBicycle(pendingBicycle, persistedBicycle);
                    savePendingBicycles(pending, index + 1);
                };
        Consumer<Exception> failure =
                error -> {
                    busy = false;
                    actions();
                    error(error);
                    StatusMessages.error(status,
                            "Клиент сохранён, но не удалось "
                                    + (operation == BicycleOperation.DELETE ? "удалить" : "сохранить")
                                    + " велосипед «"
                                    + pendingBicycle.brandName()
                                    + "». "
                                    + Objects.toString(error.getMessage(), "Сервер не сообщил причину ошибки.")
                                    + " Изменения отмечены цветом.");
                };
        if (operation == BicycleOperation.DELETE)
            context.clients.deleteBicycle(
                    client.id(), pendingBicycle.id(), pendingBicycle.version(), success, failure);
        else if (operation == BicycleOperation.CREATE)
            context.clients.createBicycle(client.id(), request, success, failure);
        else
            context.clients.updateBicycle(
                    client.id(), pendingBicycle.id(), request, success, failure);
    }

    private ClientBicycleData findPersistedBicycle(
            ClientBicycleData pending, ClientData beforeSave, ClientData afterSave) {
        if (pending.id() != null)
            return afterSave.bicycles().stream()
                    .filter(value -> pending.id().equals(value.id()))
                    .findFirst()
                    .orElse(null);
        Set<Long> existingIds =
                beforeSave.bicycles().stream()
                        .map(ClientBicycleData::id)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());
        List<ClientBicycleData> newlyCreated =
                afterSave.bicycles().stream()
                        .filter(value -> value.id() != null && !existingIds.contains(value.id()))
                        .toList();
        List<ClientBicycleData> matching =
                newlyCreated.stream()
                        .filter(
                                value ->
                                        Objects.equals(value.brandName(), pending.brandName())
                                                && Objects.equals(value.comment(), pending.comment()))
                        .toList();
        if (!matching.isEmpty()) return matching.getFirst();
        return newlyCreated.size() == 1 ? newlyCreated.getFirst() : null;
    }

    private void replacePendingBicycle(
            ClientBicycleData pending, ClientBicycleData persisted) {
        for (int index = 0; index < bicycles.getItems().size(); index++) {
            if (bicycles.getItems().get(index) == pending) {
                bicycles.getItems().set(index, persisted);
                break;
            }
        }
        pendingBicycles.remove(pending);
        bicycles.refresh();
    }

    private void removePendingBicycle(ClientBicycleData pending) {
        for (int index = 0; index < bicycles.getItems().size(); index++) {
            if (bicycles.getItems().get(index) == pending) {
                bicycles.getItems().remove(index);
                break;
            }
        }
        pendingBicycles.remove(pending);
        bicycles.refresh();
    }

    private void selectExisting(String query) {
        FxView.Loaded<ClientsController> view = FxView.load("Clients");
        var stage = FxView.dialog(form.getScene().getWindow(), "Выбор клиента", view.root());
        view.controller().bind(context);
        view.controller()
                .picker(
                        value -> {
                            saved.accept(value);
                            stage.close();
                        },
                        query == null ? "" : query);
        stage.showAndWait();
    }

    private boolean confirmDiscard() {
        if (!editing) return true;
        var alert =
                FxView.confirmation(
                        "Отменить несохранённые изменения карточки?",
                        ButtonType.YES,
                        ButtonType.NO);
        alert.initOwner(form.getScene().getWindow());
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    public boolean canLeave() {
        return !busy && confirmDiscard();
    }

    private void error(Exception error) {
        StatusMessages.error(status, error.getMessage());
    }
}
