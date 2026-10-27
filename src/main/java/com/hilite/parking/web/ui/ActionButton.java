package com.hilite.parking.web.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;

/**
 * A native {@code <button>} with an optional icon and a text label, styled entirely by
 * {@code styles.css}. Supports a busy state: the label changes, a spinner replaces the icon and
 * the button is disabled so it cannot be submitted twice.
 */
public class ActionButton extends NativeButton {

    private final Span iconSlot = new Span();
    private final Span label = new Span();
    private Component icon;

    public ActionButton(String text, Component icon, String... classNames) {
        addClassName("btn");
        addClassNames(classNames);
        iconSlot.addClassName("btn-icon-slot");
        label.addClassName("btn-label");
        add(iconSlot, label);
        setLabel(text);
        setIcon(icon);
    }

    /** Places the icon after the label (used for trailing arrows). */
    public ActionButton iconLast() {
        addClassName("icon-last");
        return this;
    }

    public final void setLabel(String text) {
        label.setText(text);
    }

    public String getLabel() {
        return label.getText();
    }

    public final void setIcon(Component newIcon) {
        icon = newIcon;
        iconSlot.removeAll();
        if (newIcon != null) {
            iconSlot.add(newIcon);
        }
        iconSlot.setVisible(newIcon != null);
    }

    /** Shows the loading state: spinner, new label, disabled. */
    public void showBusy(String busyLabel) {
        iconSlot.removeAll();
        Span spinner = new Span();
        spinner.addClassName("btn-spinner");
        spinner.getElement().setAttribute("aria-hidden", "true");
        iconSlot.add(spinner);
        iconSlot.setVisible(true);
        setLabel(busyLabel);
        getElement().setAttribute("aria-busy", "true");
        setEnabled(false);
    }

    /** Leaves the loading state with the given label and icon. */
    public void restore(String text, Component restoredIcon) {
        getElement().removeAttribute("aria-busy");
        setLabel(text);
        setIcon(restoredIcon != null ? restoredIcon : icon);
        setEnabled(true);
    }
}
