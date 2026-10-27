package com.hilite.parking.web.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Common frame of every dark kiosk screen (Entry, Allocation, Exit, Admin PIN): garage backdrop,
 * a centred shell with the shared header, a main body region and the disclaimer footer.
 *
 * <p>While attached, the page polls every {@value #TICK_MILLIS} ms; each poll refreshes the
 * header clock and then calls {@link #onTick()}. Customer kiosk pages also register the global
 * Ctrl+Shift+A shortcut and the round lock button that open the admin PIN screen.</p>
 */
public abstract class KioskPage extends Div {

    public static final int TICK_MILLIS = 500;
    public static final String KIOSK_TAGLINE = "A SMARTER PARKING EXPERIENCE AT HiLITE MALL";
    static final String ADMIN_ROUTE = "admin";

    private final KioskHeader header;
    private final Component headerAction;
    private final Div body = new Div();

    /**
     * Customer kiosk page with the admin lock button and Ctrl+Shift+A.
     */
    protected KioskPage(Clock clock, ZoneId zone, String footerCopy) {
        this(clock, zone, "SMART PARKING", footerCopy, KIOSK_TAGLINE, null, true);
    }

    protected KioskPage(Clock clock, ZoneId zone, String eyebrow, String footerCopy,
                        String tagline, Component headerAction, boolean customerKiosk) {
        addClassName("kiosk-root");
        Component action = headerAction;
        if (customerKiosk) {
            action = adminButton();
            Shortcuts.addShortcutListener(this, this::openAdmin,
                    Key.KEY_A, KeyModifier.CONTROL, KeyModifier.SHIFT);
        }
        this.headerAction = action;
        header = new KioskHeader(clock, zone, eyebrow, action);

        body.addClassName("kiosk-body");
        body.getElement().setAttribute("role", "main");

        Span info = new Span("i");
        info.addClassName("footer-info");
        info.getElement().setAttribute("aria-hidden", "true");
        Span copy = new Span(footerCopy);
        copy.addClassName("footer-copy");
        Span spacer = new Span();
        spacer.addClassName("footer-spacer");
        Span tag = new Span(tagline);
        tag.addClassName("footer-tagline");
        Div footer = new Div(info, copy, spacer, tag);
        footer.addClassName("kiosk-footer");
        footer.getElement().setAttribute("role", "contentinfo");

        Div shell = new Div(header, body, footer);
        shell.addClassName("kiosk-shell");
        add(new Backdrop(), shell);

        Ticker.whileAttached(this, TICK_MILLIS, () -> {
            header.tick();
            onTick();
        });
    }

    protected KioskHeader header() {
        return header;
    }

    protected Component headerAction() {
        return headerAction;
    }

    protected Div body() {
        return body;
    }

    /** Called on every poll after the clock refresh. */
    protected void onTick() {
    }

    private NativeButton adminButton() {
        NativeButton button = new NativeButton();
        button.addClassNames("icon-button", "admin-access-button");
        button.add(Icons.lock(17));
        button.setAriaLabel("Administrator access");
        button.getElement().setAttribute("title", "Administrator access (Ctrl+Shift+A)");
        button.addClickListener(event -> openAdmin());
        return button;
    }

    private void openAdmin() {
        getUI().ifPresent(ui -> ui.navigate(ADMIN_ROUTE));
    }

    /** Small helper: a span with text and space-separated CSS classes. */
    protected static Span text(String text, String className) {
        return Dom.text(text, className);
    }

    /** Small helper: a div with CSS classes and children. */
    protected static Div box(String className, Component... children) {
        return Dom.box(className, children);
    }
}
