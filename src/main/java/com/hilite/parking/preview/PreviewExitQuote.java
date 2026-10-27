package com.hilite.parking.preview;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/** PHASE 1 PREVIEW ONLY. Presentation values for the exit flow; no real billing occurs. */
public record PreviewExitQuote(
        String registration,
        VehicleType vehicleType,
        LocalDateTime entryTime,
        LocalDateTime exitTime,
        Duration duration,
        Duration freeAllowance,
        Duration chargeableDuration,
        String hourlyRateLabel,
        BigDecimal amountDue
) {
    public boolean paymentRequired() {
        return amountDue.signum() > 0;
    }
}
