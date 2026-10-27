package com.hilite.parking.web.security;

import com.hilite.parking.config.HiliteProperties;
import com.hilite.parking.service.AdminAccessService;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Server-side admin unlock state for one browser session.
 *
 * <p>The unlock flag lives in the server-side {@link VaadinSession}, never in the browser, so
 * hiding a button is not the only protection: every admin action goes through
 * {@link AdminOperations}, which calls {@link #requireUnlocked()} first. Wrong PINs are counted
 * per session; after {@code hilite.admin-max-attempts} failures the PIN screen locks for
 * {@code hilite.admin-lockout}. This is demonstration authentication, not an identity system.</p>
 *
 * <p>All methods must run while a Vaadin session is current (a view event or navigation). With
 * no session the check fails closed.</p>
 */
@Component
public class AdminAuthorization {

    private static final String UNLOCKED = AdminAuthorization.class.getName() + ".unlocked";
    private static final String FAILURES = AdminAuthorization.class.getName() + ".failures";
    private static final String LOCKED_UNTIL = AdminAuthorization.class.getName() + ".lockedUntil";

    private final AdminAccessService adminAccess;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration lockout;

    public AdminAuthorization(AdminAccessService adminAccess, Clock clock,
                              HiliteProperties properties) {
        this.adminAccess = Objects.requireNonNull(adminAccess, "adminAccess is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.maxAttempts = properties.adminMaxAttempts();
        this.lockout = properties.adminLockout();
    }

    /** The outcome of one PIN attempt. {@code retryAfter} is non-zero only when locked out. */
    public record PinAttempt(Status status, Duration retryAfter) {

        public enum Status { UNLOCKED, REJECTED, LOCKED_OUT }

        public PinAttempt {
            Objects.requireNonNull(status, "status is required");
            Objects.requireNonNull(retryAfter, "retryAfter is required");
        }
    }

    /**
     * Checks a PIN and unlocks this session when it is correct. While locked out the PIN is not
     * even checked, so guessing gains nothing.
     */
    public PinAttempt attemptUnlock(String candidatePin) {
        VaadinSession session = requireSession();
        Duration remaining = lockoutRemaining(session);
        if (!remaining.isZero()) {
            return new PinAttempt(PinAttempt.Status.LOCKED_OUT, remaining);
        }
        if (adminAccess.verifyPin(candidatePin)) {
            session.setAttribute(FAILURES, null);
            session.setAttribute(LOCKED_UNTIL, null);
            session.setAttribute(UNLOCKED, Boolean.TRUE);
            return new PinAttempt(PinAttempt.Status.UNLOCKED, Duration.ZERO);
        }
        int failures = failures(session) + 1;
        if (failures >= maxAttempts) {
            session.setAttribute(FAILURES, null);
            session.setAttribute(LOCKED_UNTIL, clock.instant().plus(lockout));
            return new PinAttempt(PinAttempt.Status.LOCKED_OUT, lockout);
        }
        session.setAttribute(FAILURES, failures);
        return new PinAttempt(PinAttempt.Status.REJECTED, Duration.ZERO);
    }

    /** True when the current browser session has entered the correct PIN and not locked again. */
    public boolean isUnlocked() {
        VaadinSession session = VaadinSession.getCurrent();
        return session != null && Boolean.TRUE.equals(session.getAttribute(UNLOCKED));
    }

    /** Ends admin access for this browser session. */
    public void lock() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(UNLOCKED, null);
        }
    }

    /**
     * Throws unless this browser session is unlocked. Called by every admin action.
     *
     * @throws AdminAccessDeniedException when the session is not unlocked
     */
    public void requireUnlocked() {
        if (!isUnlocked()) {
            throw new AdminAccessDeniedException();
        }
    }

    private Duration lockoutRemaining(VaadinSession session) {
        Object until = session.getAttribute(LOCKED_UNTIL);
        if (!(until instanceof Instant lockedUntil)) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(clock.instant(), lockedUntil);
        if (remaining.isNegative() || remaining.isZero()) {
            session.setAttribute(LOCKED_UNTIL, null);
            return Duration.ZERO;
        }
        return remaining;
    }

    private static int failures(VaadinSession session) {
        return session.getAttribute(FAILURES) instanceof Integer count ? count : 0;
    }

    private static VaadinSession requireSession() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            throw new AdminAccessDeniedException();
        }
        return session;
    }
}
