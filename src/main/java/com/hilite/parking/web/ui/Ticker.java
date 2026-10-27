package com.hilite.parking.web.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.shared.Registration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Server-driven timer built on Vaadin's built-in UI polling (no push, no custom JavaScript).
 *
 * <p>While {@code owner} is attached, the UI polls at {@code intervalMillis} and {@code tick}
 * runs on every poll. On detach the listener is removed and polling is switched off unless
 * another attached owner still needs it. Owners are tracked per UI so that a view being
 * replaced during navigation cannot cancel the next view's timer.</p>
 */
public final class Ticker {

    private static final String OWNERS_KEY = Ticker.class.getName() + ".owners";

    private Ticker() {
    }

    /**
     * Polls while {@code owner} is attached.
     *
     * @return a registration that stops this ticker early
     */
    public static Registration whileAttached(Component owner, int intervalMillis, Runnable tick) {
        Objects.requireNonNull(owner, "owner is required");
        Objects.requireNonNull(tick, "tick is required");
        Registration[] poll = new Registration[1];
        Registration attach = owner.addAttachListener(event -> {
            UI ui = event.getUI();
            owners(ui).put(owner, intervalMillis);
            applyInterval(ui);
            if (poll[0] != null) {
                poll[0].remove();
            }
            poll[0] = ui.addPollListener(pollEvent -> tick.run());
        });
        Registration detach = owner.addDetachListener(event -> stop(event.getUI(), owner, poll));
        return () -> {
            attach.remove();
            detach.remove();
            owner.getUI().ifPresent(ui -> stop(ui, owner, poll));
        };
    }

    private static void stop(UI ui, Component owner, Registration[] poll) {
        if (poll[0] != null) {
            poll[0].remove();
            poll[0] = null;
        }
        owners(ui).remove(owner);
        applyInterval(ui);
    }

    private static void applyInterval(UI ui) {
        int interval = owners(ui).values().stream().mapToInt(Integer::intValue).min().orElse(-1);
        ui.setPollInterval(interval);
    }

    @SuppressWarnings("unchecked")
    private static Map<Component, Integer> owners(UI ui) {
        Object current = ComponentUtil.getData(ui, OWNERS_KEY);
        if (current instanceof Map<?, ?> map) {
            return (Map<Component, Integer>) map;
        }
        Map<Component, Integer> created = new LinkedHashMap<>();
        ComponentUtil.setData(ui, OWNERS_KEY, created);
        return created;
    }
}
