package org.bike.fx.workorder;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

public final class CommentEditorController {
    @FXML private TextArea text;

    public void setText(String value) {
        text.setText(value == null ? "" : value);
    }

    public String text() {
        return text.getText();
    }

    public void focusText() {
        text.requestFocus();
    }
}
