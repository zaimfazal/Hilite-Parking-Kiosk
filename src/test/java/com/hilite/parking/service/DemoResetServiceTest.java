package com.hilite.parking.service;

import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.DatabaseInitializer;
import com.hilite.parking.repository.SettingsRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSlotRepository;
import com.hilite.parking.repository.jdbc.JdbcSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link DemoResetService} delegates to the initializer and lets failures propagate. */
class DemoResetServiceTest {

    @TempDir
    Path tempDir;

    private Database database;
    private JdbcParkingSessionRepository sessions;
    private DemoResetService resetService;

    @BeforeEach
    void setUp() {
        database = ServiceTestSupport.initializedDatabase(tempDir);
        sessions = new JdbcParkingSessionRepository(database);
        resetService = new DemoResetService(new DatabaseInitializer(database));
    }

    @Test
    void resetClearsSessionsKeepsPinAndSetsEntryMode() {
        ParkingService parking = ServiceTestSupport.newService(
                new JdbcParkingSlotRepository(database), sessions);
        assertInstanceOf(AllocationOutcome.Allocated.class,
                parking.allocateParking("KA01AB1234", VehicleType.CAR, false));
        JdbcSettingsRepository settings = new JdbcSettingsRepository(database);
        settings.saveValue(SettingsRepository.KEY_ADMIN_PIN, "2468");
        settings.saveValue(SettingsRepository.KEY_KIOSK_MODE, "EXIT");

        resetService.reset();

        assertEquals(0, sessions.countEntries());
        assertEquals(Optional.of("2468"), settings.findValue(SettingsRepository.KEY_ADMIN_PIN));
        assertEquals(Optional.of("ENTRY"), settings.findValue(SettingsRepository.KEY_KIOSK_MODE));
        // The same plate can enter again, starting from the first bay.
        AllocationOutcome.Allocated again = assertInstanceOf(AllocationOutcome.Allocated.class,
                parking.allocateParking("KA01AB1234", VehicleType.CAR, false));
        assertEquals("G-01", again.slot().slotId());
    }

    @Test
    void failurePropagatesAsDataAccessExceptionAndChangesNothing() {
        ServiceTestSupport.occupy(database, "G-01", "KA01AB1234");
        ServiceTestSupport.execute(database, """
                CREATE TRIGGER block_reset BEFORE DELETE ON parking_sessions
                BEGIN
                    SELECT RAISE(ABORT, 'test-only reset failure');
                END
                """);

        assertThrows(DataAccessException.class, resetService::reset);
        assertEquals(1, sessions.countEntries());
    }

    @Test
    void nullInitializerIsRejected() {
        assertThrows(NullPointerException.class, () -> new DemoResetService(null));
    }
}
