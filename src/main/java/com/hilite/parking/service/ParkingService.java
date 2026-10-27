package com.hilite.parking.service;

import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.ParkingSlot;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.repository.ParkingSlotRepository;
import com.hilite.parking.util.RegistrationUtil;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The entry-kiosk allocation use case.
 *
 * <p>Given a number plate, a vehicle category, and a charging preference, this service decides
 * which bay the vehicle should use and records one active parking session. The kiosk operates on
 * the result through {@link AllocationOutcome}; no JavaFX type is referenced here, so the rules
 * can be exercised without starting a UI.</p>
 *
 * <p>Both repositories are injected through the constructor. This class contains no SQL: the
 * layout and occupancy are read through {@link ParkingSlotRepository} and
 * {@link ParkingSessionRepository}. Occupancy is derived from active sessions, never from a
 * mutable flag on a bay, so the stored 24-bay layout stays read-only.</p>
 *
 * <p>Two kiosks may race for the same bay. Selection happens in memory, therefore the database
 * partial unique indexes on active registrations and active slots remain the final safeguard:
 * an insert that loses the race fails, and this service retries the next candidate instead of
 * trusting the earlier snapshot. Only a SQLite constraint refusal counts as a lost race; a
 * BUSY, I/O or other failure propagates at once, so a free lower bay is never skipped.</p>
 *
 * <p>Entry time is taken from the injected {@link Clock}; the two-argument constructor uses
 * {@link Clock#systemUTC()}. Tests can supply a fixed clock and get a deterministic timestamp.
 * This class holds no static mutable state.</p>
 */
public final class ParkingService {

    private static final String MESSAGE_VEHICLE_TYPE =
            "Select Car or Bike / Scooter to continue.";
    private static final String MESSAGE_REGISTRATION =
            "Enter a valid number plate, for example KL 11 AB 1234.";
    private static final String MESSAGE_DUPLICATE =
            "This vehicle already has an active parking session.";
    private static final String MESSAGE_CHARGING_UNAVAILABLE =
            "No EV charging bay is available right now. Try again without charging.";
    private static final String MESSAGE_PARKING_FULL =
            "The car park is full. No bay is available right now.";

    private final ParkingSlotRepository slotRepository;
    private final ParkingSessionRepository sessionRepository;
    private final Clock clock;

    /**
     * Creates the service with the system UTC clock.
     *
     * @param slotRepository    read access to the fixed bay layout
     * @param sessionRepository persistence for parking sessions
     * @throws NullPointerException if either repository is {@code null}
     */
    public ParkingService(ParkingSlotRepository slotRepository,
                          ParkingSessionRepository sessionRepository) {
        this(slotRepository, sessionRepository, Clock.systemUTC());
    }

    /**
     * Creates the service with an explicit clock, so tests can pin the entry time.
     *
     * @param slotRepository    read access to the fixed bay layout
     * @param sessionRepository persistence for parking sessions
     * @param clock             source of the entry timestamp
     * @throws NullPointerException if any argument is {@code null}
     */
    public ParkingService(ParkingSlotRepository slotRepository,
                          ParkingSessionRepository sessionRepository,
                          Clock clock) {
        this.slotRepository = Objects.requireNonNull(
                slotRepository, "slotRepository is required");
        this.sessionRepository = Objects.requireNonNull(
                sessionRepository, "sessionRepository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /**
     * Attempts to park one vehicle and record its active session.
     *
     * <p>Validation and domain refusals are returned as {@link AllocationOutcome.Rejected}.
     * A genuine {@link DataAccessException} from a read, or an insert failure that is not a
     * lost race, propagates so the caller can show a generic failure instead of a false
     * "car park full".</p>
     *
     * @param registration      number plate as typed; normalized before storage
     * @param vehicleType       car or bike / scooter; must not be {@code null}
     * @param chargingRequested whether the driver wants an EV charging bay
     * @return either a saved session with its bay, or a reason the request was refused
     */
    public AllocationOutcome allocateParking(String registration,
                                             VehicleType vehicleType,
                                             boolean chargingRequested) {
        if (vehicleType == null) {
            return new AllocationOutcome.Rejected(
                    AllocationOutcome.Reason.INVALID_REQUEST, MESSAGE_VEHICLE_TYPE);
        }

        Optional<String> validated = validateRegistration(registration);
        if (validated.isEmpty()) {
            return invalidRegistration();
        }
        String normalized = validated.get();

        // Cheap business-key check before reading the whole layout.
        if (sessionRepository.findActiveByRegistration(normalized).isPresent()) {
            return duplicate();
        }

        List<ParkingSlot> slots = slotRepository.findAll();
        Set<String> occupiedSlotIds = occupiedSlotIds(sessionRepository.findActive());
        List<ParkingSlot> candidates = vacantCandidates(slots, occupiedSlotIds, chargingRequested);

        if (candidates.isEmpty()) {
            return noBay(chargingRequested);
        }

        // One timestamp and one id for the whole attempt: retries only change the bay.
        Instant entryTime = Instant.now(clock);
        String sessionId = UUID.randomUUID().toString();
        DataAccessException lastFailure = null;

        for (ParkingSlot slot : candidates) {
            ParkingSession session = new ParkingSession(
                    sessionId,
                    normalized,
                    vehicleType,
                    chargingRequested,
                    slot.slotId(),
                    entryTime,
                    null,
                    null);
            try {
                sessionRepository.insertActive(session);
                return new AllocationOutcome.Allocated(session, slot);
            } catch (DataAccessException failure) {
                // Only a constraint refusal can mean "lost the race". BUSY, I/O and every other
                // failure is a real persistence problem: trying the next bay would skip a free
                // lower bay and break ascending order, so surface it immediately.
                if (!isLostRace(failure)) {
                    throw failure;
                }
                // The bay may have been taken by another kiosk, or this plate may already be
                // active because of a concurrent entry. Never report "full" for a duplicate.
                if (sessionRepository.findActiveByRegistration(normalized).isPresent()) {
                    return duplicate();
                }
                lastFailure = failure;
            }
        }

        // Every insert failed. Re-read occupancy once: if a suitable bay is still vacant the
        // failure was a real persistence problem, so surface it instead of faking a full park.
        Set<String> occupiedAfterFailures = occupiedSlotIds(sessionRepository.findActive());
        if (!vacantCandidates(slots, occupiedAfterFailures, chargingRequested).isEmpty()) {
            throw lastFailure;
        }
        return noBay(chargingRequested);
    }

    /**
     * Read-only lookup for the allocation confirmation view.
     *
     * <p>Returns the stored session with its bay, even when the session has since been
     * completed (the caller checks {@code session().exitTime()}). Never allocates and never
     * writes.</p>
     *
     * @param sessionId id returned by a previous allocation
     * @return the session and bay, or empty when the id is {@code null}, blank or unknown
     * @throws DataAccessException if the database cannot be read, or the session refers to a
     *                             bay that is not in the layout
     */
    public Optional<AllocationOutcome.Allocated> findAllocation(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        Optional<ParkingSession> found = sessionRepository.findById(sessionId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ParkingSession session = found.get();
        ParkingSlot slot = slotRepository.findById(session.slotId())
                .orElseThrow(() -> new DataAccessException(
                        "Parking session " + session.sessionId() + " refers to unknown bay "
                                + session.slotId()));
        return Optional.of(new AllocationOutcome.Allocated(session, slot));
    }

    /**
     * True when the insert was refused by a SQLite constraint that a concurrent entry can
     * cause: the active-slot / active-registration unique indexes ({@code SQLITE_CONSTRAINT_UNIQUE}),
     * a primary-key clash, a trigger {@code RAISE(...)} ({@code SQLITE_CONSTRAINT_TRIGGER}), or
     * a bare {@code SQLITE_CONSTRAINT} when the driver does not report an extended code. CHECK,
     * NOT NULL and foreign-key violations, BUSY, I/O and anything without a SQLite cause are
     * not races. The whole cause chain is inspected.
     */
    static boolean isLostRace(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLiteException sqlite) {
                SQLiteErrorCode code = sqlite.getResultCode();
                return code == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE
                        || code == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY
                        || code == SQLiteErrorCode.SQLITE_CONSTRAINT_TRIGGER
                        || code == SQLiteErrorCode.SQLITE_CONSTRAINT;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    /**
     * Normalizes and validates the plate, returning empty when it cannot be used.
     * A null or empty value makes {@link RegistrationUtil#normalize(String)} throw, which is
     * treated as a normal validation failure rather than a crash.
     */
    private static Optional<String> validateRegistration(String registration) {
        try {
            String normalized = RegistrationUtil.normalize(registration);
            return RegistrationUtil.isValid(normalized)
                    ? Optional.of(normalized)
                    : Optional.empty();
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    /**
     * Vacant slots of the preferred kind. Charging requests accept only charging bays; a
     * standard request prefers non-charging bays and falls back to charging bays when every
     * ordinary bay is taken. Slot order is preserved exactly as the repository returned it.
     */
    private static List<ParkingSlot> vacantCandidates(List<ParkingSlot> slots,
                                                      Set<String> occupiedSlotIds,
                                                      boolean chargingRequested) {
        List<ParkingSlot> vacant = slots.stream()
                .filter(slot -> !occupiedSlotIds.contains(slot.slotId()))
                .toList();
        if (chargingRequested) {
            return withCharging(vacant, true);
        }
        List<ParkingSlot> standard = withCharging(vacant, false);
        return standard.isEmpty() ? withCharging(vacant, true) : standard;
    }

    /** Filters slots by their fixed charging capability; the flag is never modified. */
    private static List<ParkingSlot> withCharging(List<ParkingSlot> slots, boolean charging) {
        return slots.stream()
                .filter(slot -> slot.charging() == charging)
                .toList();
    }

    /** Slot ids referenced by active sessions; such bays are occupied by definition. */
    private static Set<String> occupiedSlotIds(List<ParkingSession> activeSessions) {
        return activeSessions.stream()
                .map(ParkingSession::slotId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static AllocationOutcome invalidRegistration() {
        return new AllocationOutcome.Rejected(
                AllocationOutcome.Reason.INVALID_REGISTRATION, MESSAGE_REGISTRATION);
    }

    private static AllocationOutcome duplicate() {
        return new AllocationOutcome.Rejected(
                AllocationOutcome.Reason.DUPLICATE_ACTIVE_SESSION, MESSAGE_DUPLICATE);
    }

    /** The refusal used when no suitable bay remains, in the same order as candidate selection. */
    private static AllocationOutcome noBay(boolean chargingRequested) {
        return chargingRequested
                ? new AllocationOutcome.Rejected(
                        AllocationOutcome.Reason.CHARGING_UNAVAILABLE,
                        MESSAGE_CHARGING_UNAVAILABLE)
                : new AllocationOutcome.Rejected(
                        AllocationOutcome.Reason.PARKING_FULL,
                        MESSAGE_PARKING_FULL);
    }
}
