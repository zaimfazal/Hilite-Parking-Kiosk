package com.hilite.parking.deploy;

import com.hilite.parking.model.KioskMode;
import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.Payment;
import com.hilite.parking.model.PaymentStatus;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.repository.ParkingSlotRepository;
import com.hilite.parking.repository.jdbc.JdbcPaymentRepository;
import com.hilite.parking.service.AdminAccessService;
import com.hilite.parking.service.AllocationOutcome;
import com.hilite.parking.service.ExitOutcome;
import com.hilite.parking.service.ExitService;
import com.hilite.parking.service.ExitSummary;
import com.hilite.parking.service.ParkingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data written through the services survives an application restart on the same file (the
 * Docker volume case), and startup resets nothing.
 */
class PersistenceAcrossRestartTest {

    private static final Instant ENTRY = Instant.parse("2026-10-28T08:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void sessionsPaymentsAndSettingsSurviveARestart() {
        Path dbFile = tempDir.resolve("volume").resolve("hilite-parking.db");
        MutableClock clock = new MutableClock(ENTRY);

        String paidSessionId;
        String paymentId;
        String activeSessionId;

        try (ConfigurableApplicationContext first = DeployTestSupport.start(dbFile, clock, Map.of())) {
            assertThat(first.getBean(Database.class).file()).isEqualTo(dbFile);
            ParkingService parking = first.getBean(ParkingService.class);
            ExitService exits = first.getBean(ExitService.class);

            AllocationOutcome paid = parking.allocateParking("KL 07 CD 4321", VehicleType.CAR, false);
            AllocationOutcome stays = parking.allocateParking("KL 11 AB 1234", VehicleType.BIKE, false);
            assertThat(paid).isInstanceOf(AllocationOutcome.Allocated.class);
            assertThat(stays).isInstanceOf(AllocationOutcome.Allocated.class);
            paidSessionId = ((AllocationOutcome.Allocated) paid).session().sessionId();
            activeSessionId = ((AllocationOutcome.Allocated) stays).session().sessionId();

            // 3 h for a car: 2 h free, then 1 chargeable hour at Rs 5.
            clock.advance(Duration.ofHours(3));
            ExitOutcome quoted = exits.quote("KL 07 CD 4321");
            assertThat(quoted).isInstanceOf(ExitOutcome.Quoted.class);
            ExitOutcome completed = exits.confirmPayment(((ExitOutcome.Quoted) quoted).quote());
            assertThat(completed).isInstanceOf(ExitOutcome.Completed.class);
            ExitSummary summary = ((ExitOutcome.Completed) completed).summary();
            assertThat(summary.amountPaid()).isEqualByComparingTo("5");
            paymentId = summary.paymentId();
            assertThat(paymentId).isNotBlank();

            first.getBean(AdminAccessService.class).changeMode(KioskMode.EXIT);
        }

        try (ConfigurableApplicationContext second = DeployTestSupport.start(dbFile, clock, Map.of())) {
            ParkingSessionRepository sessions = second.getBean(ParkingSessionRepository.class);
            ParkingSlotRepository slots = second.getBean(ParkingSlotRepository.class);

            ParkingSession closed = sessions.findById(paidSessionId).orElseThrow();
            assertThat(closed.exitTime()).isEqualTo(ENTRY.plus(Duration.ofHours(3)));
            assertThat(closed.finalFee()).isEqualByComparingTo("5");

            List<Payment> payments = new JdbcPaymentRepository(second.getBean(Database.class))
                    .findBySessionId(paidSessionId);
            assertThat(payments).singleElement().satisfies(payment -> {
                assertThat(payment.paymentId()).isEqualTo(paymentId);
                assertThat(payment.status()).isEqualTo(PaymentStatus.CONFIRMED);
                assertThat(payment.amount()).isEqualByComparingTo(new BigDecimal("5"));
            });

            assertThat(sessions.findActive()).extracting(ParkingSession::sessionId)
                    .containsExactly(activeSessionId);
            assertThat(second.getBean(ParkingService.class).findAllocation(activeSessionId))
                    .isPresent();
            assertThat(sessions.countEntries()).isEqualTo(2L);
            assertThat(sessions.countCompletedExits()).isEqualTo(1L);
            assertThat(slots.count()).isEqualTo(24L);
            assertThat(second.getBean(AdminAccessService.class).currentMode())
                    .isEqualTo(KioskMode.EXIT);
            assertThat(second.getBean(AdminAccessService.class).verifyPin("1234")).isTrue();

            // The completed plate cannot be charged twice after the restart.
            assertThat(second.getBean(ExitService.class).quote("KL 07 CD 4321"))
                    .isInstanceOf(ExitOutcome.Rejected.class);
        }
    }
}
