package com.hilite.parking.service;

import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit R3: only a SQLite constraint refusal counts as "bay taken, try the next one". Any other
 * failure (BUSY, I/O, a runtime SQL error) propagates immediately, so a free lower bay is never
 * skipped. Also exercises two real concurrent allocations on one database file.
 *
 * <p><strong>How a non-constraint failure is simulated.</strong> {@code RAISE(ABORT|FAIL|...)}
 * in a trigger reports {@code SQLITE_CONSTRAINT_TRIGGER}, i.e. a constraint, so it cannot be
 * used. Instead a trigger on the G-01 insert evaluates {@code abs(-9223372036854775807 - 1)},
 * which SQLite rejects at run time with a plain {@code SQLITE_ERROR} ("integer overflow"). A
 * second test injects a real {@link SQLiteException} with {@code SQLITE_BUSY} through a
 * repository decorator, because a genuine BUSY would need a five-second lock wait.</p>
 */
class AllocationFailureTest {

    @TempDir
    Path tempDir;

    private Database database;
    private JdbcParkingSessionRepository sessions;
    private JdbcParkingSlotRepository slots;

    @BeforeEach
    void setUp() {
        database = ServiceTestSupport.initializedDatabase(tempDir);
        sessions = new JdbcParkingSessionRepository(database);
        slots = new JdbcParkingSlotRepository(database);
    }

    @Test
    void nonConstraintSqlErrorIsRethrownWithoutTryingTheNextBay() {
        ServiceTestSupport.execute(database, """
                CREATE TRIGGER overflow_on_g01
                BEFORE INSERT ON parking_sessions
                WHEN NEW.slot_id = 'G-01'
                BEGIN
                    SELECT abs(-9223372036854775807 - 1);
                END
                """);
        ParkingService service = ServiceTestSupport.newService(slots, sessions);

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> service.allocateParking("KA01AB1234", VehicleType.CAR, false));

        SQLiteException sqlite = assertInstanceOf(SQLiteException.class, failure.getCause());
        assertEquals(SQLiteErrorCode.SQLITE_ERROR, sqlite.getResultCode(),
                "The simulation must be a non-constraint failure: " + sqlite.getMessage());
        assertEquals(0, sessions.countEntries(), "No bay, in particular not G-02, may be used");
        assertTrue(sessions.findActiveBySlot("G-02").isEmpty());
    }

    @Test
    void busyFailureIsRethrownAfterExactlyOneInsertAttempt() {
        FailingInsertRepository failing = new FailingInsertRepository(sessions,
                new SQLiteException("[SQLITE_BUSY] database is locked", SQLiteErrorCode.SQLITE_BUSY));
        ParkingService service = ServiceTestSupport.newService(slots, failing);

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> service.allocateParking("KA01AB1234", VehicleType.CAR, false));

        assertSame(failing.thrown, failure);
        assertEquals(List.of("G-01"), failing.attemptedSlots,
                "A BUSY failure must not move on to the next bay");
        assertEquals(0, sessions.countEntries());
    }

    @Test
    void constraintRaceIsStillRetriedOnTheNextBay() {
        FailingInsertRepository failing = new FailingInsertRepository(sessions,
                new SQLiteException("[SQLITE_CONSTRAINT_UNIQUE] taken",
                        SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE));
        ParkingService service = ServiceTestSupport.newService(slots, failing);

        AllocationOutcome.Allocated result = assertInstanceOf(AllocationOutcome.Allocated.class,
                service.allocateParking("KA01AB1234", VehicleType.CAR, false));

        assertEquals("G-02", result.slot().slotId());
        assertEquals(List.of("G-01", "G-02"), failing.attemptedSlots);
    }

    @Test
    void lostRaceClassificationWalksTheCauseChain() {
        assertTrue(ParkingService.isLostRace(wrap(SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE)));
        assertTrue(ParkingService.isLostRace(wrap(SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY)));
        assertTrue(ParkingService.isLostRace(wrap(SQLiteErrorCode.SQLITE_CONSTRAINT_TRIGGER)));
        assertTrue(ParkingService.isLostRace(wrap(SQLiteErrorCode.SQLITE_CONSTRAINT)));
        assertTrue(ParkingService.isLostRace(new DataAccessException("outer",
                wrap(SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE))));

        assertFalse(ParkingService.isLostRace(wrap(SQLiteErrorCode.SQLITE_BUSY)));
        assertFalse(ParkingService.isLostRace(wrap(SQLiteErrorCode.SQLITE_BUSY_TIMEOUT)));
        assertFalse(ParkingService.isLostRace(wrap(SQLiteErrorCode.SQLITE_IOERR)));
        assertFalse(ParkingService.isLostRace(wrap(SQLiteErrorCode.SQLITE_ERROR)));
        assertFalse(ParkingService.isLostRace(wrap(SQLiteErrorCode.SQLITE_CONSTRAINT_CHECK)));
        assertFalse(ParkingService.isLostRace(wrap(SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY)));
        assertFalse(ParkingService.isLostRace(new DataAccessException("no SQLite cause")));
    }

    @Test
    void concurrentEntriesOfTheSamePlateCreateExactlyOneSession() throws Exception {
        for (int round = 1; round <= 5; round++) {
            String plate = "KA0" + round + "AB1234";
            List<AllocationOutcome> outcomes = race(
                    () -> newService().allocateParking(plate, VehicleType.CAR, false),
                    () -> newService().allocateParking(plate, VehicleType.CAR, false));

            long allocated = outcomes.stream()
                    .filter(AllocationOutcome.Allocated.class::isInstance).count();
            assertEquals(1, allocated, "Round " + round + ": " + outcomes);
            AllocationOutcome.Rejected rejected = outcomes.stream()
                    .filter(AllocationOutcome.Rejected.class::isInstance)
                    .map(AllocationOutcome.Rejected.class::cast)
                    .findFirst().orElseThrow();
            assertEquals(AllocationOutcome.Reason.DUPLICATE_ACTIVE_SESSION, rejected.reason());
            assertTrue(sessions.findActiveByRegistration(plate).isPresent());
        }
        assertEquals(5, sessions.countEntries());
    }

    @Test
    void concurrentEntriesOfDifferentPlatesGetTheTwoLowestBays() throws Exception {
        List<AllocationOutcome> outcomes = race(
                () -> newService().allocateParking("KA01AB0001", VehicleType.CAR, false),
                () -> newService().allocateParking("KA01AB0002", VehicleType.CAR, false));

        Set<String> bays = new HashSet<>();
        for (AllocationOutcome outcome : outcomes) {
            bays.add(assertInstanceOf(AllocationOutcome.Allocated.class, outcome).slot().slotId());
        }
        assertEquals(Set.of("G-01", "G-02"), bays);
        assertEquals(2, sessions.countEntries());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Each kiosk gets its own service and repositories over the same file. */
    private ParkingService newService() {
        return ServiceTestSupport.newService(new JdbcParkingSlotRepository(database),
                new JdbcParkingSessionRepository(database));
    }

    private static List<AllocationOutcome> race(Callable<AllocationOutcome> first,
                                                Callable<AllocationOutcome> second)
            throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<AllocationOutcome>> futures = new ArrayList<>();
            for (Callable<AllocationOutcome> task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            List<AllocationOutcome> outcomes = new ArrayList<>();
            for (Future<AllocationOutcome> future : futures) {
                outcomes.add(future.get(20, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static DataAccessException wrap(SQLiteErrorCode code) {
        return new DataAccessException("wrapped", new SQLiteException("simulated", code));
    }

    /**
     * Delegates to the real repository but fails the first insert with the supplied SQLite
     * cause, recording every slot an insert was attempted for.
     */
    private static final class FailingInsertRepository implements ParkingSessionRepository {

        private final ParkingSessionRepository delegate;
        private final SQLiteException cause;
        private final List<String> attemptedSlots = new ArrayList<>();
        private DataAccessException thrown;

        FailingInsertRepository(ParkingSessionRepository delegate, SQLiteException cause) {
            this.delegate = delegate;
            this.cause = cause;
        }

        @Override
        public void insertActive(ParkingSession session) {
            attemptedSlots.add(session.slotId());
            if (thrown == null) {
                thrown = new DataAccessException("Inserting parking session failed", cause);
                throw thrown;
            }
            delegate.insertActive(session);
        }

        @Override
        public Optional<ParkingSession> findActiveByRegistration(String registration) {
            return delegate.findActiveByRegistration(registration);
        }

        @Override
        public Optional<ParkingSession> findLatestByRegistration(String registration) {
            return delegate.findLatestByRegistration(registration);
        }

        @Override
        public Optional<ParkingSession> findById(String sessionId) {
            return delegate.findById(sessionId);
        }

        @Override
        public List<ParkingSession> findActive() {
            return delegate.findActive();
        }

        @Override
        public List<ParkingSession> findAll() {
            return delegate.findAll();
        }

        @Override
        public void updateCompleted(ParkingSession completedSession) {
            delegate.updateCompleted(completedSession);
        }

        @Override
        public long countEntries() {
            return delegate.countEntries();
        }

        @Override
        public long countCompletedExits() {
            return delegate.countCompletedExits();
        }

        @Override
        public Optional<ParkingSession> findActiveBySlot(String slotId) {
            return delegate.findActiveBySlot(slotId);
        }
    }
}
