package com.hilite.parking.service;

import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.jdbc.JdbcExitCompletionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ParkingService#findAllocation(String)}: read-only lookup, never allocates. */
class FindAllocationTest {

    @TempDir
    Path tempDir;

    private Database database;
    private JdbcParkingSessionRepository sessions;
    private ParkingService service;

    @BeforeEach
    void setUp() {
        database = ServiceTestSupport.initializedDatabase(tempDir);
        sessions = new JdbcParkingSessionRepository(database);
        service = ServiceTestSupport.newService(new JdbcParkingSlotRepository(database), sessions);
    }

    @Test
    void knownActiveSessionReturnsTheSameAllocation() {
        AllocationOutcome.Allocated allocated = allocate("KA01AB1234", true);

        Optional<AllocationOutcome.Allocated> found =
                service.findAllocation(allocated.session().sessionId());

        assertEquals(Optional.of(allocated), found);
        assertEquals("G-03", found.orElseThrow().slot().slotId());
        assertNull(found.orElseThrow().session().exitTime());
        assertEquals(1, sessions.countEntries(), "A lookup must never allocate");
    }

    @Test
    void completedSessionIsStillReturnedWithItsExitTime() {
        AllocationOutcome.Allocated allocated = allocate("KA01AB1234", false);
        String id = allocated.session().sessionId();
        Instant exit = ServiceTestSupport.FIXED_INSTANT.plus(Duration.ofMinutes(20));
        assertTrue(new JdbcExitCompletionRepository(database)
                .complete(id, exit, BigDecimal.ZERO, null));

        AllocationOutcome.Allocated found = service.findAllocation(id).orElseThrow();

        assertEquals(exit, found.session().exitTime());
        assertEquals(allocated.slot(), found.slot());
        assertEquals(1, sessions.countEntries());
        assertTrue(sessions.findActive().isEmpty(), "A lookup must not reactivate a session");
    }

    @Test
    void unknownSessionIdIsEmpty() {
        allocate("KA01AB1234", false);

        assertEquals(Optional.empty(), service.findAllocation("no-such-session"));
        assertEquals(1, sessions.countEntries());
    }

    @Test
    void nullOrBlankSessionIdIsEmpty() {
        assertEquals(Optional.empty(), service.findAllocation(null));
        assertEquals(Optional.empty(), service.findAllocation(""));
        assertEquals(Optional.empty(), service.findAllocation("   "));
        assertEquals(0, sessions.countEntries());
    }

    private AllocationOutcome.Allocated allocate(String plate, boolean charging) {
        return assertInstanceOf(AllocationOutcome.Allocated.class,
                service.allocateParking(plate, VehicleType.CAR, charging));
    }
}
