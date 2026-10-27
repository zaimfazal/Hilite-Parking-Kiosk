package com.hilite.parking.web.kiosk;

import com.hilite.parking.web.ui.Icons;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

/**
 * The "IND" number-plate field with its own labelled clear button. Disabled until the simulated
 * scan finishes; accepts at most 16 characters; shown upper-case (normalized on submit by the
 * service).
 */
final class PlateInput extends TextField {

    static final int MAX_LENGTH = 16;

    private final NativeButton clearButton = new NativeButton();

    PlateInput() {
        super("Vehicle number plate");
        addClassName("plate-field");
        setMaxLength(MAX_LENGTH);
        setPlaceholder("Waiting for scan…");
        setValueChangeMode(ValueChangeMode.LAZY);
        setValueChangeTimeout(250);
        setAutocapitalize(com.vaadin.flow.component.textfield.Autocapitalize.CHARACTERS);
        setAutocorrect(false);
        setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete.OFF);

        Span prefix = new Span("IND");
        prefix.addClassName("plate-prefix");
        prefix.getElement().setAttribute("aria-hidden", "true");
        setPrefixComponent(prefix);

        clearButton.addClassName("field-clear-button");
        clearButton.add(Icons.close(14));
        clearButton.setAriaLabel("Clear number plate");
        clearButton.addClickListener(event -> {
            clear();
            focus();
        });
        setSuffixComponent(clearButton);
        addValueChangeListener(event -> syncClearButton());
        setEnabled(false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        syncClearButton();
    }

    NativeButton clearButton() {
        return clearButton;
    }

    private void syncClearButton() {
        if (clearButton != null) {
            clearButton.setEnabled(isEnabled() && !getValue().isBlank());
        }
    }
}
