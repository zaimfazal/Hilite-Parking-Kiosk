package com.hilite.parking.repository;

import java.util.Map;
import java.util.Optional;

/**
 * Small key/value store for demo settings that must survive a restart.
 *
 * <p>Only two keys exist in Phase 3: the demo admin PIN and the last kiosk mode. Values are
 * plain text. This is not an authentication system and holds no secrets.</p>
 */
public interface SettingsRepository {

    /** Demo admin PIN; default value {@code "1234"}. */
    String KEY_ADMIN_PIN = "admin.pin";

    /** Last kiosk mode; default value {@code "ENTRY"}. */
    String KEY_KIOSK_MODE = "kiosk.mode";

    /** Finds one setting value. */
    Optional<String> findValue(String key);

    /** Returns every setting, ordered by key. */
    Map<String, String> findAll();

    /** Inserts or replaces one setting value. */
    void saveValue(String key, String value);
}
