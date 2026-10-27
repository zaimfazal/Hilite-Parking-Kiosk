package com.hilite.parking.service;

import com.hilite.parking.model.VehicleType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Calculates parking fees from an entry time and an exit time.
 *
 * <p>Frozen rules:</p>
 * <ul>
 *   <li>The first 120 elapsed minutes (7,200 seconds) are free, inclusive.</li>
 *   <li>After the free allowance, any positive chargeable duration rounds up to the next whole
 *       hour.</li>
 *   <li>Car rate: Rs 5 per chargeable hour. Bike rate: Rs 2 per chargeable hour.</li>
 * </ul>
 *
 * <p>Precision: elapsed time is measured in whole seconds using
 * {@code Duration.between(entryTime, exitTime).toSeconds()}. Sub-second remainders are truncated
 * <em>before</em> the free allowance is subtracted, and the exact remaining seconds are then
 * rounded up to whole hours (no intermediate truncation to whole minutes or hours). For example,
 * a stay of exactly 120 minutes plus 500 milliseconds is treated as exactly 120 minutes and is
 * free.</p>
 *
 * <p>This class is the only place where fee arithmetic lives. It is stateless, deterministic
 * from its three arguments, and has no dependency on wall-clock time, the UI, or the
 * database.</p>
 */
public final class FeeCalculator {

    /** Inclusive free parking allowance: 120 minutes. */
    public static final Duration FREE_ALLOWANCE = Duration.ofMinutes(120);

    private static final long FREE_ALLOWANCE_SECONDS = FREE_ALLOWANCE.toSeconds();

    /** Number of whole seconds in one billable hour. */
    private static final long SECONDS_PER_HOUR = 60L * 60L;

    /** Car rate in rupees per chargeable hour. */
    private static final BigDecimal CAR_HOURLY_RATE = new BigDecimal("5");

    /** Bike rate in rupees per chargeable hour. */
    private static final BigDecimal BIKE_HOURLY_RATE = new BigDecimal("2");

    /**
     * Calculates the fee due for a stay.
     *
     * @param vehicleType the vehicle category; must not be {@code null}
     * @param entryTime   the parking entry instant; must not be {@code null}
     * @param exitTime    the parking exit instant; must not be {@code null}
     * @return the fee due, non-negative and with scale 0 ({@link BigDecimal#ZERO} for free stays)
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code exitTime} is before {@code entryTime}
     */
    public BigDecimal calculateFee(VehicleType vehicleType, Instant entryTime, Instant exitTime) {
        Objects.requireNonNull(vehicleType, "vehicleType must not be null");
        Objects.requireNonNull(entryTime, "entryTime must not be null");
        Objects.requireNonNull(exitTime, "exitTime must not be null");

        if (exitTime.isBefore(entryTime)) {
            throw new IllegalArgumentException("exitTime must not be before entryTime");
        }

        long elapsedSeconds = Duration.between(entryTime, exitTime).toSeconds();
        long chargeableSeconds = elapsedSeconds - FREE_ALLOWANCE_SECONDS;

        if (chargeableSeconds <= 0L) {
            return BigDecimal.ZERO;
        }

        long billableHours = Math.ceilDiv(chargeableSeconds, SECONDS_PER_HOUR);
        return hourlyRate(vehicleType).multiply(BigDecimal.valueOf(billableHours));
    }

    /**
     * Returns the hourly rate for a vehicle category.
     *
     * <p>This is the same value {@link #calculateFee} uses. The exit workflow reads it to show
     * "Rs 5 per hour" in a fee quote without keeping a second rate table.</p>
     *
     * @throws NullPointerException if {@code vehicleType} is {@code null}
     */
    public BigDecimal hourlyRate(VehicleType vehicleType) {
        Objects.requireNonNull(vehicleType, "vehicleType must not be null");
        return switch (vehicleType) {
            case CAR -> CAR_HOURLY_RATE;
            case BIKE -> BIKE_HOURLY_RATE;
        };
    }
}
