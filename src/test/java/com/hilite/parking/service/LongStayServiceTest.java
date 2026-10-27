package com.hilite.parking.service;

import com.hilite.parking.model.Floor;
import com.hilite.parking.model.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects the twelve-hour long-stay rule against a real temporary SQLite database.
 *
 * <p>The injected clock is fixed at {@link AdminTestSupport#NOW}; every entry time is derived
 * from it, so the whole-second boundary (exactly 12 h, 12 h + 500 ms, 12 h + 1 s) is exact.</p>
 */
class LongStayServiceTest {

    private static final Instant NOW = AdminTestSupport.NOW;
    private static final Duration TWELVE_HOURS = Duration.ofHours(12);

    @TempDir
    Path tempDir;

    private AdminTestSupport.Fixture fixture;
    private LongStayService service;

    @BeforeEach
    void setUp() {
        fixture = AdminTestSupport.fixture(tempDir);
        service = new LongStayService(fixture.slots, fixture.sessions,
                AdminTestSupport.FIXED_CLOCK);
    }

    @Test
    void emptyDatabaseHasNoAlerts() {
        assertTrue(service.activeAlerts().isEmpty());
    }

    @Test
    void exactlyTwelveHoursIsNotAnAlert() {
        fixture.active("S-1", "KA01AA0001", VehicleType.CAR, "G-01", NOW.minus(TWELVE_HOURS));

        assertTrue(service.activeAlerts().isEmpty());
    }

    @Test
    void twelveHoursAndHalfASecondIsNotAnAlert() {
        fixture.active("S-1", "KA01AA0001", VehicleType.CAR, "G-01",
                NOW.minus(TWELVE_HOURS).minusMillis(500));

        assertTrue(service.activeAlerts().isEmpty());
    }

    @Test
    void twelveHoursAndOneSecondIsAnAlert() {
        fixture.active("S-1", "KA01AA0001", VehicleType.CAR, "G-01",
                NOW.minus(TWELVE_HOURS).minusSeconds(1));

        List<LongStayAlert> alerts = service.activeAlerts();

        assertEquals(1, alerts.size());
        assertEquals(Duration.ofSeconds(43_201), alerts.get(0).elapsed());
    }

    @Test
    void entryInTheFutureIsNotAnAlert() {
        fixture.active("S-1", "KA01AA0001", VehicleType.CAR, "G-01", NOW.plusSeconds(3600));

        assertTrue(service.activeAlerts().isEmpty());
    }

    @Test
    void completedLongStayIsExcluded() {
        fixture.completed("S-1", "KA01AA0001", VehicleType.CAR, "G-01",
                NOW.minus(Duration.ofHours(30)), NOW.minus(Duration.ofHours(1)));

        assertTrue(service.activeAlerts().isEmpty());
    }

    @Test
    void alertCarriesEveryField() {
        Instant entry = NOW.minus(Duration.ofHours(14)).minusMillis(750);
        fixture.active("S-EV", "KA05EV0042", VehicleType.CAR, "F1-05", entry, true);

        List<LongStayAlert> alerts = service.activeAlerts();

        assertEquals(List.of(new LongStayAlert("S-EV", "KA05EV0042", VehicleType.CAR,
                Floor.FIRST, "F1-05", entry, Duration.ofHours(14), true)), alerts);
    }

    @Test
    void alertsAreLongestFirstThenBySlotId() {
        fixture.active("S-13-G05", "KA01AA0001", VehicleType.CAR, "G-05",
                NOW.minus(Duration.ofHours(13)));
        fixture.active("S-20", "KA01AA0002", VehicleType.BIKE, "F1-02",
                NOW.minus(Duration.ofHours(20)));
        fixture.active("S-13-G02", "KA01AA0003", VehicleType.CAR, "G-02",
                NOW.minus(Duration.ofHours(13)));
        fixture.active("S-13-F101", "KA01AA0004", VehicleType.BIKE, "F1-01",
                NOW.minus(Duration.ofHours(13)).minusMillis(400));
        fixture.active("S-5", "KA01AA0005", VehicleType.CAR, "G-01",
                NOW.minus(Duration.ofHours(5)));

        List<String> order = service.activeAlerts().stream()
                .map(LongStayAlert::sessionId)
                .toList();

        // F1-01 is 400 ms longer but equal in whole seconds, so slot id decides the tie.
        assertEquals(List.of("S-20", "S-13-F101", "S-13-G02", "S-13-G05"), order);
        assertEquals(Floor.GROUND, service.activeAlerts().get(2).floor());
    }

    @Test
    void alertListIsUnmodifiable() {
        fixture.active("S-1", "KA01AA0001", VehicleType.CAR, "G-01",
                NOW.minus(Duration.ofHours(13)));

        List<LongStayAlert> alerts = service.activeAlerts();

        assertThrows(UnsupportedOperationException.class, alerts::clear);
    }

    @Test
    void nullConstructorArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> new LongStayService(
                null, fixture.sessions, AdminTestSupport.FIXED_CLOCK));
        assertThrows(NullPointerException.class, () -> new LongStayService(
                fixture.slots, null, AdminTestSupport.FIXED_CLOCK));
        assertThrows(NullPointerException.class, () -> new LongStayService(
                fixture.slots, fixture.sessions, null));
    }
}
