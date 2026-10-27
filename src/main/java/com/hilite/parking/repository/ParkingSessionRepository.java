package com.hilite.parking.repository;

import com.hilite.parking.model.ParkingSession;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for parking sessions.
 *
 * <p>A session is <strong>active</strong> when both {@code exit_time} and
 * {@code final_fee_paise} are null, and <strong>completed</strong> when both are present.
 * The database enforces that pairing, and two partial unique indexes guarantee at most one
 * active session per registration and per slot.</p>
 *
 * <p>Registrations must be normalized with
 * {@link com.hilite.parking.util.RegistrationUtil#normalize(String)} before they reach this
 * repository. Lookup methods expect that normalized value; the insert method rejects a
 * non-normalized registration instead of silently changing it.</p>
 */
public interface ParkingSessionRepository {

    /**
     * Inserts a new active session.
     *
     * @throws IllegalArgumentException if the session is already completed, or its registration
     *                                  is not normalized
     * @throws DataAccessException      if the slot does not exist, or the registration or slot
     *                                  already has an active session
     */
    void insertActive(ParkingSession session);

    /** Finds the single active session for a normalized registration. */
    Optional<ParkingSession> findActiveByRegistration(String normalizedRegistration);

    /**
     * Finds the most recent session for a normalized registration, active or completed.
     *
     * <p>The exit kiosk uses this to tell an unknown plate apart from a plate whose session has
     * already been closed. Ordering is by entry time, then session id, so the result is
     * deterministic even if a plate had several historic stays.</p>
     */
    Optional<ParkingSession> findLatestByRegistration(String normalizedRegistration);

    /** Finds a session by identifier, active or completed. */
    Optional<ParkingSession> findById(String sessionId);

    /** Lists active sessions in entry-time order (then session id for deterministic ties). */
    List<ParkingSession> findActive();

    /** Lists every session in entry-time order (then session id for deterministic ties). */
    List<ParkingSession> findAll();

    /**
     * Completes one currently active session.
     *
     * <p>The update only applies while the stored {@code exit_time} is still null, so a
     * repeated completion cannot double-charge or overwrite the first result.</p>
     *
     * @throws IllegalArgumentException if the supplied session is not completed
     * @throws DataAccessException      if no active session with that id exists
     */
    void updateCompleted(ParkingSession completedSession);

    /** Counts every stored session (all entries). Returns zero on an empty table. */
    long countEntries();

    /** Counts completed sessions (all recorded exits). Returns zero on an empty table. */
    long countCompletedExits();

    /** Finds the active session occupying a slot, if any. */
    Optional<ParkingSession> findActiveBySlot(String slotId);
}
