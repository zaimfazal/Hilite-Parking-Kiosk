package com.hilite.parking.web.admin;

import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.web.kiosk.KioskHomeView;
import com.hilite.parking.web.security.AdminAuthorization;
import com.hilite.parking.web.ui.ActionButton;
import com.hilite.parking.web.ui.Icons;
import com.hilite.parking.web.ui.InlineMessage;
import com.hilite.parking.web.ui.KioskPage;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

/**
 * Administrator PIN gate. The PIN is checked on the server by {@link AdminAuthorization}, which
 * counts wrong attempts per browser session and locks out temporarily. The PIN is never shown or
 * hinted. Keypad keys append digits without moving keyboard focus.
 */
@Route("admin")
@PageTitle("Administrator Access · HiLITE Mall")
public class AdminPinView extends KioskPage implements BeforeEnterObserver {

    static final String INVALID_PIN = "Invalid PIN. Please try again.";
    static final int PIN_LENGTH = 4;

    private final AdminAuthorization authorization;

    final PasswordField pinField = new PasswordField();
    final InlineMessage error = new InlineMessage(InlineMessage.Kind.ERROR, "pin-error");
    final ActionButton unlockButton =
            new ActionButton("Unlock Dashboard", Icons.lock(17), "btn-gold", "pin-unlock");
    final ActionButton returnButton;
    private final Div pinCard;
    private boolean shakeToggle;

    public AdminPinView(AdminAuthorization authorization, Clock clock, ZoneId mallTimeZone) {
        super(clock, mallTimeZone, "PARKING MANAGEMENT",
                "Administrator area — no customer parking data is modified by unlocking.",
                "HiLITE MALL · PARKING MANAGEMENT",
                new ActionButton("Return to kiosk", Icons.logout(16), "btn-ghost"), false);
        this.authorization = authorization;
        this.returnButton = (ActionButton) headerAction();
        addClassNames("admin-pin-root");
        header().setTitle("ADMINISTRATOR ACCESS");
        returnButton.addClickListener(event -> returnToKiosk());

        pinField.addClassName("pin-field");
        pinField.setAriaLabel("Administrator PIN");
        pinField.setPlaceholder("Enter 4-digit PIN");
        pinField.setMaxLength(PIN_LENGTH);
        pinField.setAllowedCharPattern("[0-9]");
        pinField.setRevealButtonVisible(false);
        pinField.setValueChangeMode(ValueChangeMode.EAGER);
        pinField.setAutofocus(true);
        pinField.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                error.hide();
            }
        });
        pinField.addKeyPressListener(Key.ENTER, event -> handleUnlock());

        Div keypad = new Div();
        keypad.addClassName("pin-keypad");
        keypad.getElement().setAttribute("role", "group");
        keypad.getElement().setAttribute("aria-label", "PIN keypad");
        for (int digit = 1; digit <= 9; digit++) {
            keypad.add(digitKey(Integer.toString(digit)));
        }
        NativeButton clear = key("Clear");
        clear.addClassName("pin-key-small");
        clear.addClickListener(event -> handleClear());
        NativeButton backspace = new NativeButton();
        backspace.addClassName("pin-key");
        backspace.add(Icons.backspace(19));
        backspace.setAriaLabel("Backspace");
        backspace.addClickListener(event -> handleBackspace());
        keypad.add(clear, digitKey("0"), backspace);

        unlockButton.addClickListener(event -> handleUnlock());

        H1 title = new H1("Administrator Access");
        title.addClassName("pin-title");
        Paragraph subtitle = new Paragraph("Enter your demo PIN to continue.");
        subtitle.addClassName("pin-subtitle");
        Paragraph note = new Paragraph(
                "Demonstration authentication only. The PIN is not a production credential.");
        note.addClassName("pin-note");
        pinCard = box("glass-panel pin-card", box("shield-badge", Icons.shield(30)), title, subtitle,
                pinField, error, keypad, unlockButton, note);
        body().addClassName("pin-body");
        body().add(pinCard);

        Shortcuts.addShortcutListener(this, this::returnToKiosk, Key.ESCAPE);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (authorization.isUnlocked()) {
            event.forwardTo(DashboardView.class);
        }
    }

    private NativeButton digitKey(String digit) {
        NativeButton key = key(digit);
        key.addClickListener(event -> appendDigit(digit));
        return key;
    }

    private static NativeButton key(String label) {
        NativeButton key = new NativeButton(label);
        key.addClassName("pin-key");
        return key;
    }

    void appendDigit(String digit) {
        String current = pinField.getValue();
        if (current.length() >= PIN_LENGTH) {
            return;
        }
        error.hide();
        pinField.setValue(current + digit);
    }

    void handleClear() {
        pinField.clear();
        error.hide();
    }

    void handleBackspace() {
        String current = pinField.getValue();
        if (!current.isEmpty()) {
            pinField.setValue(current.substring(0, current.length() - 1));
        }
        error.hide();
    }

    void handleUnlock() {
        String candidate = pinField.getValue();
        pinField.clear();
        AdminAuthorization.PinAttempt attempt;
        try {
            attempt = authorization.attemptUnlock(candidate);
        } catch (DataAccessException failure) {
            showError("The PIN could not be checked right now. Please try again.");
            return;
        }
        switch (attempt.status()) {
            case UNLOCKED -> getUI().ifPresent(ui -> ui.navigate(DashboardView.class));
            case REJECTED -> showError(INVALID_PIN);
            case LOCKED_OUT -> showError("Too many incorrect PINs. Try again in "
                    + seconds(attempt.retryAfter()) + " seconds.");
        }
    }

    private void showError(String message) {
        error.show(message);
        // Alternate two identical animations so the shake replays on every failure.
        shakeToggle = !shakeToggle;
        pinCard.removeClassNames("shake-a", "shake-b");
        pinCard.addClassName(shakeToggle ? "shake-a" : "shake-b");
        pinField.focus();
    }

    private static long seconds(Duration duration) {
        return Math.max(1, (duration.toMillis() + 999) / 1000);
    }

    private void returnToKiosk() {
        getUI().ifPresent(ui -> ui.navigate(KioskHomeView.class));
    }
}
