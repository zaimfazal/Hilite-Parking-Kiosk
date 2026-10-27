package com.hilite.parking.service;

import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.ParkingSlot;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.repository.ParkingSlotRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only detection of active sessions parked for more than twelve hours.
 *
 * <p>Elapsed time follows the fee calculator's whole-second policy: a session alerts only when
 * {@code Duration.between(entryTime, now).getSeconds()} is strictly greater than 43,200. So
 * exactly 12 h and 12 h + 500 ms do not alert, while 12 h + 1 s does. A session whose entry
 * time is after "now" (clock skew) has negative elapsed seconds and never alerts.</p>
 *
 * <p>The slot layout and the active sessions are each read once per call. Completed sessions
 * are never reported. Nothing is written.</p>
 */
public final class LongStayService {

    /** Stays strictly longer than this, in whole seconds, raise an alert. */
    public static final Duration THRESHOLD = Duration.ofHours(12);

    private static final Comparator<LongStayAlert> LONGEST_FIRST =
            Comparator.comparing(LongStayAlert::elapsed).reversed()
                    .thenComparing(LongStayAlert::slotId);

    private final ParkingSlotRepository slotRepository;
    private final ParkingSessionRepository sessionRepository;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param slotRepository    read access to the fixed bay layout (for the floor)
     * @param sessionRepository read access to active sessions
     * @param clock             source of "now"
     * @throws NullPointerException if any argument is {@code null}
     */
    public LongStayService(ParkingSlotRepository slotRepository,
                           ParkingSessionRepository sessionRepository,
                           Clock clock) {
        this.slotRepository = Objects.requireNonNull(slotRepository, "slotRepository is required");
        this.sessionRepository = Objects.requireNonNull(
                sessionRepository, "sessionRepository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /**
     * Lists active long-stay alerts, longest stay first, then by ascending slot id.
     *
     * @return an unmodifiable list, empty when no active session exceeds the threshold
     * @throws IllegalStateException if an active session references a slot missing from the
     *                               layout
     * @throws com.hilite.parking.repository.DataAccessException if a read fails
     */
    public List<LongStayAlert> activeAlerts() {
        Instant now = clock.instant();
        long thresholdSeconds = THRESHOLD.getSeconds();

        Map<String, ParkingSlot> slotsById = new HashMap<>();
        for (ParkingSlot slot : slotRepository.findAll()) {
            slotsById.put(slot.slotId(), slot);
        }

        List<LongStayAlert> alerts = new ArrayList<>();
        for (ParkingSession session : sessionRepository.findActive()) {
            if (session.exitTime() != null) {
                continue;
            }
            long wholeSeconds = Duration.between(session.entryTime(), now).getSeconds();
            if (wholeSeconds <= thresholdSeconds) {
                continue;
            }
            ParkingSlot slot = slotsById.get(session.slotId());
            if (slot == null) {
                throw new IllegalStateException("Active session " + session.sessionId()
                        + " references unknown slot " + session.slotId());
            }
            alerts.add(new LongStayAlert(
                    session.sessionId(),
                    session.registration(),
                    session.vehicleType(),
                    slot.floor(),
                    session.slotId(),
                    session.entryTime(),
                    Duration.ofSeconds(wholeSeconds),
                    session.chargingRequested()));
        }
        alerts.sort(LONGEST_FIRST);
        return List.copyOf(alerts);
    }
}
