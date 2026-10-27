package com.hilite.parking.web.ui;

import com.vaadin.flow.component.html.Div;

/**
 * Inline error or info banner. Errors use {@code role="alert"} so screen readers announce them
 * immediately; info notices use {@code aria-live="polite"}. Text is always set as plain text.
 */
public class InlineMessage extends Div {

    public enum Kind { ERROR, INFO, SUCCESS }

    private Kind kind;

    public InlineMessage(Kind kind, String... extraClasses) {
        addClassName("inline-message");
        addClassNames(extraClasses);
        setKind(kind);
        setVisible(false);
    }

    public final void setKind(Kind newKind) {
        kind = newKind;
        removeClassNames("is-error", "is-info", "is-success");
        addClassName(switch (newKind) {
            case ERROR -> "is-error";
            case INFO -> "is-info";
            case SUCCESS -> "is-success";
        });
        if (newKind == Kind.ERROR) {
            getElement().setAttribute("role", "alert");
            getElement().removeAttribute("aria-live");
        } else {
            getElement().setAttribute("role", "status");
            getElement().setAttribute("aria-live", "polite");
        }
    }

    public Kind getKind() {
        return kind;
    }

    public void show(String message) {
        setText(message);
        setVisible(true);
    }

    public void show(Kind newKind, String message) {
        setKind(newKind);
        show(message);
    }

    public void hide() {
        setVisible(false);
        setText("");
    }

    public boolean isShowing() {
        return isVisible();
    }
}
