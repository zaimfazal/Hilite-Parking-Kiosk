package com.hilite.parking.web.security;

import com.hilite.parking.deploy.MutableClock;
import com.hilite.parking.deploy.MutableClockConfig;
import com.hilite.parking.web.security.AdminAuthorization.PinAttempt;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PIN unlock, re-lock and per-session throttling, with a real {@link VaadinSession} made current
 * by {@link SpringBrowserlessTest} (a fresh session for every test method).
 */
@SpringBootTest
@Import(MutableClockConfig.class)
class AdminAuthorizationTest extends SpringBrowserlessTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration LOCKOUT = Duration.ofSeconds(30);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("hilite.db-path", () -> tempDir.resolve("hilite-parking.db").toString());
        registry.add("hilite.admin-max-attempts", () -> MAX_ATTEMPTS);
        registry.add("hilite.admin-lockout", () -> "30s");
    }

    @Autowired
    AdminAuthorization authorization;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.set(MutableClockConfig.START);
        assertThat(VaadinSession.getCurrent()).as("browserless session").isNotNull();
    }

    @Test
    void newSessionIsLocked() {
        assertThat(authorization.isUnlocked()).isFalse();
        assertThatThrownBy(authorization::requireUnlocked)
                .isInstanceOf(AdminAccessDeniedException.class)
                .hasMessage("Administrator access is required.");
    }

    @Test
    void correctPinUnlocksAndLockRelocks() {
        PinAttempt attempt = authorization.attemptUnlock("1234");

        assertThat(attempt.status()).isEqualTo(PinAttempt.Status.UNLOCKED);
        assertThat(attempt.retryAfter()).isZero();
        assertThat(authorization.isUnlocked()).isTrue();
        authorization.requireUnlocked();

        authorization.lock();

        assertThat(authorization.isUnlocked()).isFalse();
        assertThatThrownBy(authorization::requireUnlocked)
                .isInstanceOf(AdminAccessDeniedException.class);
    }

    @Test
    void wrongPinIsRejectedAndStaysLocked() {
        for (String wrong : new String[] {"0000", "", "12345", "123", null}) {
            PinAttempt attempt = authorization.attemptUnlock(wrong);
            assertThat(attempt.status()).as("PIN %s", wrong)
                    .isEqualTo(PinAttempt.Status.REJECTED);
            assertThat(attempt.retryAfter()).isZero();
            assertThat(authorization.isUnlocked()).isFalse();

            // A correct PIN clears the counter, so this loop never reaches the lockout.
            assertThat(authorization.attemptUnlock("1234").status())
                    .isEqualTo(PinAttempt.Status.UNLOCKED);
            authorization.lock();
        }
    }

    @Test
    void tooManyWrongPinsLockOutEvenTheCorrectPinUntilTheLockoutElapses() {
        for (int i = 1; i < MAX_ATTEMPTS; i++) {
            assertThat(authorization.attemptUnlock("0000").status())
                    .isEqualTo(PinAttempt.Status.REJECTED);
        }
        PinAttempt lockedOut = authorization.attemptUnlock("0000");
        assertThat(lockedOut.status()).isEqualTo(PinAttempt.Status.LOCKED_OUT);
        assertThat(lockedOut.retryAfter()).isEqualTo(LOCKOUT);

        PinAttempt correctWhileLocked = authorization.attemptUnlock("1234");
        assertThat(correctWhileLocked.status()).isEqualTo(PinAttempt.Status.LOCKED_OUT);
        assertThat(authorization.isUnlocked()).isFalse();

        clock.advance(Duration.ofSeconds(29));
        PinAttempt almost = authorization.attemptUnlock("1234");
        assertThat(almost.status()).isEqualTo(PinAttempt.Status.LOCKED_OUT);
        assertThat(almost.retryAfter()).isEqualTo(Duration.ofSeconds(1));
        assertThat(authorization.isUnlocked()).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(authorization.attemptUnlock("1234").status())
                .isEqualTo(PinAttempt.Status.UNLOCKED);
        assertThat(authorization.isUnlocked()).isTrue();
    }

    @Test
    void failureCounterStartsAgainAfterALockout() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            authorization.attemptUnlock("0000");
        }
        clock.advance(LOCKOUT);

        // A fresh allowance: MAX_ATTEMPTS - 1 wrong PINs are only rejected, not locked out.
        for (int i = 1; i < MAX_ATTEMPTS; i++) {
            assertThat(authorization.attemptUnlock("0000").status())
                    .isEqualTo(PinAttempt.Status.REJECTED);
        }
        assertThat(authorization.attemptUnlock("0000").status())
                .isEqualTo(PinAttempt.Status.LOCKED_OUT);
    }

    @Test
    void correctPinResetsTheFailureCount() {
        for (int i = 1; i < MAX_ATTEMPTS; i++) {
            authorization.attemptUnlock("0000");
        }
        assertThat(authorization.attemptUnlock("1234").status())
                .isEqualTo(PinAttempt.Status.UNLOCKED);
        authorization.lock();

        for (int i = 1; i < MAX_ATTEMPTS; i++) {
            assertThat(authorization.attemptUnlock("0000").status())
                    .isEqualTo(PinAttempt.Status.REJECTED);
        }
    }

    @Test
    void noVaadinSessionFailsClosed() {
        VaadinSession current = VaadinSession.getCurrent();
        authorization.attemptUnlock("1234");
        assertThat(authorization.isUnlocked()).isTrue();
        try {
            VaadinSession.setCurrent(null);

            assertThat(authorization.isUnlocked()).isFalse();
            assertThatThrownBy(authorization::requireUnlocked)
                    .isInstanceOf(AdminAccessDeniedException.class);
            assertThatThrownBy(() -> authorization.attemptUnlock("1234"))
                    .isInstanceOf(AdminAccessDeniedException.class);
            authorization.lock(); // no session: a harmless no-op, never an exception
        } finally {
            VaadinSession.setCurrent(current);
        }
        // The real session is unaffected by the calls made without one.
        assertThat(authorization.isUnlocked()).isTrue();
    }

    @Test
    void unlockIsPerBrowserSession() {
        authorization.attemptUnlock("1234");
        assertThat(authorization.isUnlocked()).isTrue();

        VaadinSession first = VaadinSession.getCurrent();
        // A second browser session of the same service, with its own (held) session lock.
        ReentrantLock otherLock = new ReentrantLock();
        VaadinSession other = new VaadinSession(first.getService()) {
            @Override
            public Lock getLockInstance() {
                return otherLock;
            }
        };
        otherLock.lock();
        try {
            VaadinSession.setCurrent(other);
            assertThat(authorization.isUnlocked()).isFalse();
            assertThatThrownBy(authorization::requireUnlocked)
                    .isInstanceOf(AdminAccessDeniedException.class);
        } finally {
            otherLock.unlock();
            VaadinSession.setCurrent(first);
        }
        assertThat(authorization.isUnlocked()).isTrue();
    }
}
