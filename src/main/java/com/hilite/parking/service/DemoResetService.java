package com.hilite.parking.service;

import com.hilite.parking.repository.DatabaseInitializer;

import java.util.Objects;

/**
 * Thin business boundary for the admin "Reset demo" action.
 *
 * <p>All of the work, and the all-or-nothing guarantee, lives in
 * {@link DatabaseInitializer#resetDemoData()}: payments and sessions are cleared, the frozen
 * 24-bay layout is restored if needed, an existing admin PIN is kept, and the kiosk mode is set
 * to ENTRY, in one transaction. Authorization is the caller's job (the admin layer).</p>
 */
public final class DemoResetService {

    private final DatabaseInitializer initializer;

    /**
     * Creates the service.
     *
     * @throws NullPointerException if {@code initializer} is {@code null}
     */
    public DemoResetService(DatabaseInitializer initializer) {
        this.initializer = Objects.requireNonNull(initializer, "initializer is required");
    }

    /**
     * Resets the demo data.
     *
     * @throws com.hilite.parking.repository.DataAccessException if the reset fails; nothing is
     *                                                           changed in that case
     */
    public void reset() {
        initializer.resetDemoData();
    }
}
