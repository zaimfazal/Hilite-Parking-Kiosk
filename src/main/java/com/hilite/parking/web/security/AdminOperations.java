package com.hilite.parking.web.security;

import com.hilite.parking.model.KioskMode;
import com.hilite.parking.service.AdminAccessService;
import com.hilite.parking.service.DashboardStatistics;
import com.hilite.parking.service.DemoResetService;
import com.hilite.parking.service.LongStayAlert;
import com.hilite.parking.service.LongStayService;
import com.hilite.parking.service.StatisticsService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * The only way the admin views reach admin business services.
 *
 * <p>Every method checks {@link AdminAuthorization#requireUnlocked()} on the server before doing
 * anything, so an admin action cannot run from a session that has not entered the PIN, even if a
 * request is crafted by hand. The services themselves stay UI-agnostic.</p>
 */
@Component
public class AdminOperations {

    private final AdminAuthorization authorization;
    private final StatisticsService statistics;
    private final LongStayService longStays;
    private final AdminAccessService adminAccess;
    private final DemoResetService demoReset;

    public AdminOperations(AdminAuthorization authorization, StatisticsService statistics,
                           LongStayService longStays, AdminAccessService adminAccess,
                           DemoResetService demoReset) {
        this.authorization = Objects.requireNonNull(authorization, "authorization is required");
        this.statistics = Objects.requireNonNull(statistics, "statistics is required");
        this.longStays = Objects.requireNonNull(longStays, "longStays is required");
        this.adminAccess = Objects.requireNonNull(adminAccess, "adminAccess is required");
        this.demoReset = Objects.requireNonNull(demoReset, "demoReset is required");
    }

    /** Live dashboard figures from the shared database. */
    public DashboardStatistics statistics() {
        authorization.requireUnlocked();
        return statistics.snapshot();
    }

    /** Active sessions parked strictly longer than 12 hours, longest first. */
    public List<LongStayAlert> longStayAlerts() {
        authorization.requireUnlocked();
        return longStays.activeAlerts();
    }

    /** The persisted kiosk mode (Entry by default). */
    public KioskMode kioskMode() {
        authorization.requireUnlocked();
        return adminAccess.currentMode();
    }

    /** Persists the kiosk mode. Parking sessions, bays and payments are not touched. */
    public void changeKioskMode(KioskMode mode) {
        authorization.requireUnlocked();
        adminAccess.changeMode(mode);
    }

    /**
     * Clears all sessions and payments, restores the 24-bay layout, keeps the PIN and sets the
     * kiosk to Entry, in one transaction. Returns only after the transaction commits.
     *
     * @throws com.hilite.parking.repository.DataAccessException if the reset rolled back
     */
    public void resetDemoData() {
        authorization.requireUnlocked();
        demoReset.reset();
    }
}
