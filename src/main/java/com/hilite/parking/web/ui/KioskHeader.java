package com.hilite.parking.web.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Shared kiosk header: brand lockup on the left, optional screen title in the centre, and the
 * live mall-time clock plus one action (admin lock button or "Return to kiosk") on the right.
 * The clock text is refreshed by {@link #tick()}, which the owning view calls on each poll.
 */
public class KioskHeader extends Div {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private final Clock clock;
    private final ZoneId zone;
    private final Span date = new Span();
    private final Span time = new Span();
    private final Span title = new Span();

    /**
     * @param eyebrow brand sub-line, e.g. "SMART PARKING" or "PARKING MANAGEMENT"
     * @param action  the right-hand control, or {@code null}
     */
    public KioskHeader(Clock clock, ZoneId zone, String eyebrow, Component action) {
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.zone = Objects.requireNonNull(zone, "zone is required");
        addClassName("kiosk-header");
        getElement().setAttribute("role", "banner");

        Span strong = new Span("HiLITE");
        strong.addClassName("brand-strong");
        Span light = new Span("MALL");
        light.addClassName("brand-light");
        Span divider = new Span();
        divider.addClassName("brand-divider");
        divider.getElement().setAttribute("aria-hidden", "true");
        Span sub = new Span(eyebrow);
        sub.addClassName("brand-eyebrow");
        Div brand = new Div(strong, light, divider, sub);
        brand.addClassName("brand-lockup");
        brand.getElement().setAttribute("aria-label", "HiLITE Mall " + eyebrow.toLowerCase(Locale.ROOT));

        title.addClassName("kiosk-header-title");
        title.setVisible(false);

        date.addClassName("header-date");
        time.addClassName("header-time");
        Div clockBox = new Div(date, time);
        clockBox.addClassName("header-clock");
        clockBox.getElement().setAttribute("aria-label", "Current mall time");
        Div right = new Div(clockBox);
        right.addClassName("header-right");
        if (action != null) {
            right.add(action);
        }
        add(brand, title, right);
        tick();
    }

    /** Shows or hides the centre title. */
    public void setTitle(String text) {
        boolean has = text != null && !text.isBlank();
        title.setText(has ? text : "");
        title.setVisible(has);
    }

    /** Refreshes the clock from the injected clock in the mall's zone. */
    public final void tick() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        date.setText(DATE.format(now));
        time.setText(TIME.format(now));
    }

    String dateText() {
        return date.getText();
    }

    String timeText() {
        return time.getText();
    }
}
