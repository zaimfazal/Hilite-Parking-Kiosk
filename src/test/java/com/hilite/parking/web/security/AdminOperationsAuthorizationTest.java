package com.hilite.parking.web.security;

import com.hilite.parking.model.KioskMode;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.service.AdminAccessService;
import com.hilite.parking.service.AllocationOutcome;
import com.hilite.parking.service.DemoResetService;
import com.hilite.parking.service.ParkingService;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.server.VaadinSession;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every {@link AdminOperations} method is guarded on the server: from a locked (or absent)
 * session it throws {@link AdminAccessDeniedException} and changes nothing.
 */
@SpringBootTest
class AdminOperationsAuthorizationTest extends SpringBrowserlessTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("hilite.db-path", () -> tempDir.resolve("hilite-parking.db").toString());
    }

    @Autowired
    AdminOperations operations;

    @Autowired
    AdminAuthorization authorization;

    @Autowired
    ParkingService parking;

    @Autowired
    ParkingSessionRepository sessions;

    @Autowired
    AdminAccessService adminAccess;

    @Autowired
    DemoResetService demoReset;

    private String sessionId;

    @BeforeEach
    void freshDataWithOneParkedCar() {
        demoReset.reset(); // known state per test: no sessions, mode ENTRY
        AllocationOutcome outcome = parking.allocateParking("KL 07 CD 4321", VehicleType.CAR, false);
        assertThat(outcome).isInstanceOf(AllocationOutcome.Allocated.class);
        sessionId = ((AllocationOutcome.Allocated) outcome).session().sessionId();
        assertThat(authorization.isUnlocked()).isFalse();
    }

    @Test
    void lockedSessionCannotRunAnyAdminOperation() {
        assertEveryOperationDenied();
        assertNothingChanged();
    }

    @Test
    void wrongPinDoesNotGrantAccess() {
        authorization.attemptUnlock("0000");

        assertEveryOperationDenied();
        assertNothingChanged();
    }

    @Test
    void unlockedSessionCanRunOperationsAndLockRevokesAccess() {
        assertThat(authorization.attemptUnlock("1234").status())
                .isEqualTo(AdminAuthorization.PinAttempt.Status.UNLOCKED);

        assertThat(operations.statistics()).isNotNull();
        assertThat(operations.longStayAlerts()).isEmpty();
        assertThat(operations.kioskMode()).isEqualTo(KioskMode.ENTRY);
        operations.changeKioskMode(KioskMode.EXIT);
        assertThat(adminAccess.currentMode()).isEqualTo(KioskMode.EXIT);

        authorization.lock();

        assertEveryOperationDenied();
        assertThat(adminAccess.currentMode()).isEqualTo(KioskMode.EXIT);
        assertThat(sessions.findById(sessionId)).isPresent();
    }

    @Test
    void unlockedResetClearsSessionsAndKeepsThePin() {
        authorization.attemptUnlock("1234");
        operations.changeKioskMode(KioskMode.EXIT);

        operations.resetDemoData();

        assertThat(sessions.findAll()).isEmpty();
        assertThat(adminAccess.currentMode()).isEqualTo(KioskMode.ENTRY);
        assertThat(adminAccess.verifyPin("1234")).isTrue();
    }

    @Test
    void noVaadinSessionFailsClosed() {
        authorization.attemptUnlock("1234");
        VaadinSession current = VaadinSession.getCurrent();
        try {
            VaadinSession.setCurrent(null);
            assertEveryOperationDenied();
        } finally {
            VaadinSession.setCurrent(current);
        }
        assertNothingChanged();
    }

    private void assertEveryOperationDenied() {
        List<ThrowingCallable> calls = List.of(
                operations::statistics,
                operations::longStayAlerts,
                operations::kioskMode,
                () -> operations.changeKioskMode(KioskMode.EXIT),
                () -> operations.changeKioskMode(KioskMode.ENTRY),
                operations::resetDemoData);
        for (ThrowingCallable call : calls) {
            assertThatThrownBy(call)
                    .isInstanceOf(AdminAccessDeniedException.class)
                    .hasMessage("Administrator access is required.");
        }
    }

    private void assertNothingChanged() {
        assertThat(sessions.findById(sessionId)).isPresent();
        assertThat(sessions.findActive()).hasSize(1);
        assertThat(adminAccess.currentMode()).isEqualTo(KioskMode.ENTRY);
    }
}
