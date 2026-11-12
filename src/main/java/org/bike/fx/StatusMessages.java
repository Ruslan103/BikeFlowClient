package org.bike.fx;

import javafx.css.PseudoClass;
import javafx.scene.control.Label;

public final class StatusMessages {
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");

    private StatusMessages() { }

    public static void install(Label label) {
        label.textProperty().addListener(
                (observable, oldText, newText) -> label.pseudoClassStateChanged(WARNING, false));
    }

    public static void error(Label label, String message) {
        label.setText(message == null || message.isBlank() ? "Произошла ошибка." : message);
        label.pseudoClassStateChanged(WARNING, true);
    }
}
