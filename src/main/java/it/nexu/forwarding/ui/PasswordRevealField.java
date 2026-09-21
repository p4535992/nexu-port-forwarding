package it.nexu.forwarding.ui;

import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

/** Password input with an explicit show/hide control. */
public final class PasswordRevealField extends HBox {
    private final PasswordField hidden = new PasswordField();
    private final TextField visible = new TextField();
    private final ToggleButton reveal = new ToggleButton("👁");

    public PasswordRevealField() {
        super(6);
        hidden.textProperty().bindBidirectional(visible.textProperty());
        visible.setVisible(false); visible.setManaged(false);
        StackPane fields = new StackPane(hidden, visible);
        fields.setMaxWidth(Double.MAX_VALUE);
        setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(fields, Priority.ALWAYS);
        hidden.setMaxWidth(Double.MAX_VALUE); visible.setMaxWidth(Double.MAX_VALUE);
        reveal.setFocusTraversable(false);
        reveal.setTooltip(new Tooltip("Mostra / nascondi password"));
        reveal.selectedProperty().addListener((o, a, shown) -> {
            hidden.setVisible(!shown); hidden.setManaged(!shown);
            visible.setVisible(shown); visible.setManaged(shown);
            if (shown) visible.requestFocus(); else hidden.requestFocus();
            int caret = shown ? visible.getLength() : hidden.getLength();
            if (shown) visible.positionCaret(caret); else hidden.positionCaret(caret);
        });
        getChildren().addAll(fields, reveal);
    }
    public String getText() { return hidden.getText(); }
    public int getLength() { return hidden.getLength(); }
    public void clear() { hidden.clear(); reveal.setSelected(false); }
    public void setPromptText(String text) { hidden.setPromptText(text); visible.setPromptText(text); }
    public void requestInputFocus() { (reveal.isSelected() ? visible : hidden).requestFocus(); }
}
