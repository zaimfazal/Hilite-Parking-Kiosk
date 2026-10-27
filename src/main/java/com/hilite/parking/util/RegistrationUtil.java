package com.hilite.parking.util;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One deterministic normalization rule for vehicle registrations.
 *
 * <p>Display text may keep spaces ("KL 11 AB 1234"), but the persisted business key does not.
 * Normalization removes all whitespace and hyphens, then uppercases using {@link Locale#ROOT},
 * so {@code "kl 11-ab 1234"} becomes {@code "KL11AB1234"}. This is the only implementation
 * used for duplicate checks and database lookups.</p>
 *
 * <p>The accepted format is deliberately simple and suits the fictional Indian demo plates:
 * two letters, two digits, one to three letters, then four digits (for example
 * {@code KA01AB1234}). It is not a government registration validator.</p>
 */
public final class RegistrationUtil {

    private static final Pattern VALID_NORMALIZED =
            Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z]{1,3}[0-9]{4}");

    private RegistrationUtil() {
    }

    /**
     * Normalizes a registration for storage and comparison.
     *
     * @param raw text as typed or scanned; may contain spaces or hyphens
     * @return uppercase registration without separators
     * @throws IllegalArgumentException if the value is null or contains no usable characters
     */
    public static String normalize(String raw) {
        Objects.requireNonNull(raw, "registration is required");
        String compact = raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (compact.isEmpty()) {
            throw new IllegalArgumentException("registration must not be empty");
        }
        return compact;
    }

    /** Returns true when the value is already normalized and matches the demo plate format. */
    public static boolean isValid(String normalized) {
        return normalized != null && VALID_NORMALIZED.matcher(normalized).matches();
    }
}
