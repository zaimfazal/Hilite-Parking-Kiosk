package com.hilite.parking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Deployment settings, bound from {@code hilite.*} properties or the matching environment
 * variables ({@code HILITE_DB_PATH}, {@code HILITE_TIME_ZONE}, ...).
 *
 * @param dbPath           SQLite file; a relative path resolves against the working directory
 * @param timeZone         the mall's local zone, used for display and hourly reporting
 * @param adminMaxAttempts wrong PINs allowed before a temporary lock-out
 * @param adminLockout     how long the PIN screen stays locked after too many wrong PINs
 */
@ConfigurationProperties("hilite")
public record HiliteProperties(
        @DefaultValue("data/hilite-parking.db") Path dbPath,
        @DefaultValue("Asia/Kolkata") ZoneId timeZone,
        @DefaultValue("5") int adminMaxAttempts,
        @DefaultValue("30s") Duration adminLockout
) {

    public HiliteProperties {
        Objects.requireNonNull(dbPath, "hilite.db-path is required");
        Objects.requireNonNull(timeZone, "hilite.time-zone is required");
        Objects.requireNonNull(adminLockout, "hilite.admin-lockout is required");
        if (adminMaxAttempts < 1) {
            throw new IllegalArgumentException("hilite.admin-max-attempts must be at least 1");
        }
        if (adminLockout.isNegative()) {
            throw new IllegalArgumentException("hilite.admin-lockout must not be negative");
        }
    }
}
