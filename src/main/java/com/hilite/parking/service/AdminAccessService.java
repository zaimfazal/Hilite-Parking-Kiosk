package com.hilite.parking.service;

import com.hilite.parking.model.KioskMode;
import com.hilite.parking.repository.SettingsRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;

/**
 * PIN check and persisted kiosk mode for the demo admin area.
 *
 * <p>This service is UI-agnostic: it answers "is this the admin PIN?" and reads or writes the
 * last kiosk mode. Authorization (who may call {@link #changeMode}) is enforced by the web
 * layer, not here.</p>
 *
 * <p>The stored PIN is never returned, logged, or included in an exception message or
 * {@link #toString()}. Comparison uses {@link MessageDigest#isEqual(byte[], byte[])} on UTF-8
 * bytes so the time taken does not depend on how many leading characters match.</p>
 *
 * <p>The stored kiosk mode is parsed strictly: only the exact enum names {@code ENTRY} and
 * {@code EXIT} are recognised (surrounding whitespace is ignored). Anything else, including
 * lowercase {@code exit}, falls back to {@link KioskMode#ENTRY}, because ENTRY is the safe
 * default screen and {@link #changeMode} only ever writes the exact enum name.</p>
 */
public final class AdminAccessService {

    /** PIN used when no {@code admin.pin} setting is stored. */
    public static final String DEFAULT_PIN = "1234";

    private final SettingsRepository settings;

    /**
     * Creates the service.
     *
     * @param settings key/value settings store holding {@code admin.pin} and {@code kiosk.mode}
     * @throws NullPointerException if {@code settings} is {@code null}
     */
    public AdminAccessService(SettingsRepository settings) {
        this.settings = Objects.requireNonNull(settings, "settings is required");
    }

    /**
     * Checks a candidate PIN against the stored admin PIN.
     *
     * <p>The candidate is trimmed first. A {@code null} or blank candidate is always rejected.
     * When no PIN is stored, {@link #DEFAULT_PIN} applies.</p>
     *
     * @param candidate the PIN typed by the user; may be {@code null}
     * @return {@code true} only when the trimmed candidate equals the stored PIN
     */
    public boolean verifyPin(String candidate) {
        if (candidate == null) {
            return false;
        }
        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        String stored = settings.findValue(SettingsRepository.KEY_ADMIN_PIN).orElse(DEFAULT_PIN);
        return MessageDigest.isEqual(
                trimmed.getBytes(StandardCharsets.UTF_8),
                stored.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the persisted kiosk mode.
     *
     * <p>An absent, blank, or unrecognised stored value yields {@link KioskMode#ENTRY}; bad
     * data never causes an exception here.</p>
     *
     * @return the stored mode, or {@code ENTRY} when it is not an exact enum name
     */
    public KioskMode currentMode() {
        Optional<String> stored = settings.findValue(SettingsRepository.KEY_KIOSK_MODE);
        if (stored.isEmpty()) {
            return KioskMode.ENTRY;
        }
        String value = stored.get().trim();
        for (KioskMode mode : KioskMode.values()) {
            if (mode.name().equals(value)) {
                return mode;
            }
        }
        return KioskMode.ENTRY;
    }

    /**
     * Persists the kiosk mode as its enum name. Only the {@code kiosk.mode} setting is written;
     * sessions, payments, slots, and the PIN are untouched.
     *
     * @param mode the new mode
     * @throws NullPointerException if {@code mode} is {@code null}
     */
    public void changeMode(KioskMode mode) {
        Objects.requireNonNull(mode, "mode is required");
        settings.saveValue(SettingsRepository.KEY_KIOSK_MODE, mode.name());
    }

    /** Describes the service without revealing any stored setting. */
    @Override
    public String toString() {
        return "AdminAccessService";
    }
}
