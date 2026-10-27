package com.hilite.parking.web.admin;

import com.hilite.parking.model.Floor;
import com.hilite.parking.model.KioskMode;
import com.hilite.parking.model.ParkingSession;
import com.hilite.parking.model.ParkingSlot;
import com.hilite.parking.model.VehicleType;
import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.service.BayStatus;
import com.hilite.parking.service.DashboardStatistics;
import com.hilite.parking.service.FloorOccupancy;
import com.hilite.parking.service.HourlyEntries;
import com.hilite.parking.service.LongStayAlert;
import com.hilite.parking.util.FormatUtil;
import com.hilite.parking.web.kiosk.KioskHomeView;
import com.hilite.parking.web.security.AdminAccessDeniedException;
import com.hilite.parking.web.security.AdminAuthorization;
import com.hilite.parking.web.security.AdminOperations;
import com.hilite.parking.web.ui.ActionButton;
import com.hilite.parking.web.ui.Icons;
import com.hilite.parking.web.ui.Plates;
import com.hilite.parking.web.ui.InlineMessage;
import com.hilite.parking.web.ui.Ticker;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.hilite.parking.web.ui.Dom.box;
import static com.hilite.parking.web.ui.Dom.text;

/**
 * Administrator dashboard: live occupancy, floor maps, EV usage, hourly entries, long-stay
 * alerts, the kiosk-mode selector and the demo reset.
 *
 * <p>Every figure comes from {@link AdminOperations}, which re-checks the server-side unlock on
 * each call; a locked session is rerouted to the PIN screen. The view refreshes itself every
 * {@value #REFRESH_MILLIS} ms and keeps the selected bay.</p>
 */
@Route("admin/dashboard")
@PageTitle("Dashboard · HiLITE Mall")
public class DashboardView extends Div implements BeforeEnterObserver {

    static final int REFRESH_MILLIS = 10_000;
    static final String RESET_COPY = "Clears all parking sessions and demo payments, frees all 24 "
            + "bays, keeps the admin PIN and sets the kiosk to Entry. This cannot be undone.";
    private static final DateTimeFormatter UPDATED =
            DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);

    private final AdminAuthorization authorization;
    private final AdminOperations operations;
    private final ZoneId zone;
    private final Clock clock;

    // top bar
    final NativeButton entryModeButton = modeButton("Entry Kiosk", Icons.steering(16));
    final NativeButton exitModeButton = modeButton("Exit Kiosk", Icons.logout(16));
    final ActionButton resetButton = new ActionButton("Reset Demo", Icons.reset(15), "btn-danger-ghost");
    final ActionButton lockButton = new ActionButton("Lock & return to kiosk", Icons.logout(16), "sidebar-lock");
    final Span updatedLabel = text("", "admin-updated");
    final InlineMessage notice = new InlineMessage(InlineMessage.Kind.INFO, "admin-inline");
    final InlineMessage dataError = new InlineMessage(InlineMessage.Kind.ERROR, "admin-inline");

    // KPI cards
    final Span occupancyValue = text("—", "stat-value");
    final Span occupancyHint = text("—", "stat-hint");
    final Span availableValue = text("—", "stat-value");
    final Span availableHint = text("—", "stat-hint");
    final Span entriesValue = text("—", "stat-value");
    final Span entriesHint = text("—", "stat-hint");
    final Span exitsValue = text("—", "stat-value");
    final Span exitsHint = text("—", "stat-hint");

    // floor maps
    final Map<Floor, Div> floorGrids = new LinkedHashMap<>();
    final Map<Floor, Span> floorPills = new LinkedHashMap<>();
    final Map<String, NativeButton> bayTiles = new LinkedHashMap<>();

    // side column
    final Span bayDetailTitle = text("Select a bay", "bay-detail-title");
    final Span bayDetailStatus = text("", "status-pill");
    final Span bayDetailMeta = text("Choose any bay on the floor maps to inspect it.", "bay-detail-meta");
    final Div bayDetailSession = box("bay-detail-session");
    final Span carsSummary = text("—", "snapshot-value");
    final Span bikesSummary = text("—", "snapshot-value");
    final Span evUsage = text("—", "snapshot-value");
    final Div evBar = box("ev-usage-fill");
    final Div evTrack = box("ev-usage-bar", evBar);
    final Span peakValue = text("—", "peak-value");
    final Span peakHint = text("—", "stat-hint");
    final Div chart = box("hour-chart");
    final Span longStayCount = text("0", "alert-count");
    final Div alertsList = box("alerts-list");

    // reset dialog
    final Dialog resetDialog = new Dialog();
    final ActionButton cancelResetButton = new ActionButton("Cancel", Icons.close(15), "btn-light");
    final ActionButton confirmResetButton = new ActionButton("Confirm Reset", Icons.reset(15), "btn-danger");

    private DashboardStatistics lastStats;
    private String selectedBayId;

    public DashboardView(AdminAuthorization authorization, AdminOperations operations,
                         ZoneId mallTimeZone, Clock clock) {
        this.authorization = authorization;
        this.operations = operations;
        this.zone = mallTimeZone;
        this.clock = clock;
        addClassName("admin-root");

        Div main = box("admin-main", topBar(), workspace());
        main.getElement().setAttribute("role", "main");
        add(sidebar(), main);
        buildResetDialog();

        entryModeButton.addClickListener(event -> changeMode(KioskMode.ENTRY));
        exitModeButton.addClickListener(event -> changeMode(KioskMode.EXIT));
        resetButton.addClickListener(event -> resetDialog.open());
        lockButton.addClickListener(event -> lock());

        Ticker.whileAttached(this, REFRESH_MILLIS, this::refresh);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!authorization.isUnlocked()) {
            // forwardTo (not rerouteTo) so the address bar shows /admin, not the locked route.
            event.forwardTo(AdminPinView.class);
            return;
        }
        refresh();
    }

    // ---- layout ----------------------------------------------------------------------------

    private Component sidebar() {
        Div brand = box("sidebar-brand",
                box("sidebar-brand-line", text("HiLITE", "sidebar-brand-strong"),
                        text("MALL", "sidebar-brand-light")),
                text("PARKING MANAGEMENT", "sidebar-brand-sub"));
        Span nav = new Span();
        nav.addClassName("sidebar-nav-active");
        nav.add(Icons.dashboard(19), text("Dashboard", "sidebar-nav-label"));
        nav.getElement().setAttribute("aria-current", "page");
        Div navBox = box("sidebar-nav", text("OPERATIONS", "sidebar-section"), nav);
        navBox.getElement().setAttribute("role", "navigation");
        navBox.getElement().setAttribute("aria-label", "Administration");

        Span avatar = text("A", "admin-avatar");
        avatar.getElement().setAttribute("aria-hidden", "true");
        Div footer = box("sidebar-footer",
                box("sidebar-user-row", avatar,
                        box("sidebar-user-copy", text("Administrator", "sidebar-user"),
                                text("Unlocked session", "sidebar-user-sub"))),
                lockButton,
                text("A GREATER TOMORROW TOGETHER", "sidebar-tagline"));
        Div sidebar = box("admin-sidebar", brand, navBox, box("sidebar-grow"), footer);
        sidebar.getElement().setAttribute("role", "complementary");
        return sidebar;
    }

    private Component topBar() {
        H1 title = new H1("Dashboard");
        title.addClassName("admin-title");
        Div heading = box("admin-heading", title,
                box("admin-subtitle-row", text("Live parking status and operations overview",
                        "admin-subtitle"), updatedLabel));
        Span modeLabel = text("Kiosk mode", "mode-label");
        modeLabel.setId("kiosk-mode-label");
        Div modeSwitch = box("mode-switch", entryModeButton, exitModeButton);
        modeSwitch.getElement().setAttribute("role", "group");
        modeSwitch.getElement().setAttribute("aria-labelledby", "kiosk-mode-label");
        return box("admin-topbar", heading, box("topbar-spacer"),
                box("mode-box", modeLabel, modeSwitch), resetButton);
    }

    private Component workspace() {
        Div kpis = box("kpi-row",
                statCard(Icons.car(21), "", "Current occupancy", occupancyValue, occupancyHint),
                statCard(Icons.check(20), "available", "Available bays", availableValue, availableHint),
                statCard(Icons.arrowUp(20), "entries", "Total vehicle entries", entriesValue, entriesHint),
                statCard(Icons.logout(20), "exits", "Total vehicle exits", exitsValue, exitsHint));

        Div maps = box("maps-column", floorCard(Floor.GROUND), floorCard(Floor.FIRST));
        Div side = box("side-column", bayDetailCard(), glanceCard(), analyticsCard(), alertsCard());
        Div workspace = box("admin-workspace", notice, dataError, kpis,
                box("dashboard-columns", maps, side));
        return box("admin-scroll", workspace);
    }

    private static Div statCard(Component icon, String variant, String label, Span value, Span hint) {
        Div iconTile = box("stat-icon", icon);
        if (!variant.isEmpty()) {
            iconTile.addClassName(variant);
        }
        return box("stat-card", box("stat-head", iconTile,
                box("stat-copy", text(label, "stat-label"), value)), hint);
    }

    private Div floorCard(Floor floor) {
        H2 heading = new H2(floor.displayName());
        heading.addClassName("panel-heading");
        String range = floor.code() + "-01 to " + floor.code() + "-12 · Zones A and B";
        Span pill = text("—", "count-pill");
        floorPills.put(floor, pill);
        Div grid = box("bay-grid");
        grid.getElement().setAttribute("role", "group");
        grid.getElement().setAttribute("aria-label", floor.displayName() + " bays");
        floorGrids.put(floor, grid);
        return box("panel-card floor-card",
                box("panel-head", box("panel-head-copy", heading, text(range, "panel-subheading")), pill),
                grid);
    }

    private Div bayDetailCard() {
        H3 heading = new H3("Bay details");
        heading.addClassName("panel-heading-small");
        bayDetailStatus.setVisible(false);
        Div card = box("panel-card bay-detail-card", box("panel-head", heading),
                bayDetailTitle, box("bay-detail-status-row", bayDetailStatus, bayDetailMeta),
                bayDetailSession);
        card.getElement().setAttribute("aria-live", "polite");
        return card;
    }

    private Div glanceCard() {
        H3 heading = new H3("At a glance");
        heading.addClassName("panel-heading-small");
        evTrack.getElement().setAttribute("role", "progressbar");
        evTrack.getElement().setAttribute("aria-label", "EV charging bays in use");
        evTrack.getElement().setAttribute("aria-valuemin", "0");
        return box("panel-card",
                box("panel-head", heading, text("CARS / BIKES", "subtle-pill")),
                box("snapshot-list",
                        snapshotRow(Icons.car(15), "Cars", carsSummary),
                        snapshotRow(Icons.bike(15), "Bikes / scooters", bikesSummary),
                        box("card-divider"),
                        box("snapshot-row", box("tiny-icon ev", Icons.bolt(15)),
                                box("snapshot-ev", box("snapshot-ev-head", text("EV charging", "snapshot-label"),
                                        evUsage), evTrack))));
    }

    private static Div snapshotRow(Component icon, String label, Span value) {
        return box("snapshot-row", box("tiny-icon", icon), text(label, "snapshot-label"),
                box("snapshot-spacer"), value);
    }

    private Div analyticsCard() {
        H3 heading = new H3("Entries by hour");
        heading.addClassName("panel-heading-small");
        chart.getElement().setAttribute("role", "img");
        return box("panel-card peak-card",
                box("panel-head", heading, text("TODAY & EARLIER", "subtle-pill")),
                box("peak-row", box("stat-icon small", Icons.clock(18)),
                        box("stat-copy", text("Peak entry hour", "stat-label"), peakValue),
                        box("snapshot-spacer"), peakHint),
                chart);
    }

    private Div alertsCard() {
        H3 heading = new H3("Long-stay alerts");
        heading.addClassName("panel-heading-small");
        alertsList.getElement().setAttribute("role", "list");
        alertsList.getElement().setAttribute("aria-label", "Vehicles parked longer than 12 hours");
        return box("panel-card alerts-card",
                box("panel-head", heading, longStayCount, box("snapshot-spacer"),
                        text("> 12 HOURS", "subtle-pill")),
                alertsList);
    }

    private static NativeButton modeButton(String label, Component icon) {
        NativeButton button = new NativeButton();
        button.addClassName("mode-button");
        button.add(icon, text(label, "mode-button-label"));
        return button;
    }

    private void buildResetDialog() {
        resetDialog.addClassName("admin-dialog");
        resetDialog.setModal(true);
        resetDialog.setCloseOnEsc(true);
        resetDialog.setCloseOnOutsideClick(false);
        resetDialog.getElement().setAttribute("aria-label", "Reset demo data?");
        H2 title = new H2("Reset demo data?");
        title.addClassName("dialog-title");
        Paragraph copy = new Paragraph(RESET_COPY);
        copy.addClassName("dialog-copy");
        resetDialog.add(box("admin-dialog-card", box("dialog-icon", Icons.alert(24)), title, copy,
                box("dialog-actions", cancelResetButton, confirmResetButton)));
        cancelResetButton.addClickListener(event -> resetDialog.close());
        confirmResetButton.addClickListener(event -> confirmReset());
        resetDialog.addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                cancelResetButton.focus();
            } else {
                resetButton.focus();
            }
        });
        add(resetDialog);
    }

    // ---- data ------------------------------------------------------------------------------

    /** Reloads every figure from the database. Keeps the selected bay. */
    void refresh() {
        DashboardStatistics stats;
        List<LongStayAlert> alerts;
        KioskMode mode;
        try {
            stats = operations.statistics();
            alerts = operations.longStayAlerts();
            mode = operations.kioskMode();
        } catch (AdminAccessDeniedException denied) {
            toPinScreen();
            return;
        } catch (DataAccessException failure) {
            dataError.show("Live data could not be loaded. The dashboard will try again shortly.");
            return;
        }
        dataError.hide();
        lastStats = stats;
        populateKpis(stats);
        populateFloors(stats);
        populateGlance(stats);
        populateChart(stats);
        populateAlerts(alerts);
        showMode(mode);
        showBayDetails(selectedBayId);
        updatedLabel.setText("Updated " + UPDATED.format(ZonedDateTime.now(clock).withZoneSameInstant(zone)));
    }

    private void populateKpis(DashboardStatistics stats) {
        int total = stats.totalBays();
        occupancyValue.setText(Integer.toString(stats.occupiedBays()));
        occupancyHint.setText("of " + total + " bays · " + percent(stats.occupiedBays(), total) + "%");
        availableValue.setText(Integer.toString(stats.availableBays()));
        availableHint.setText("of " + total + " bays · " + percent(stats.availableBays(), total) + "%");
        entriesValue.setText(Long.toString(stats.totalEntries()));
        entriesHint.setText("Cars " + stats.carsEntered() + " · Bikes " + stats.bikesEntered());
        exitsValue.setText(Long.toString(stats.totalExits()));
        exitsHint.setText("Cars " + stats.carsExited() + " · Bikes " + stats.bikesExited());
    }

    private static long percent(long value, long total) {
        return total == 0 ? 0 : Math.round(value * 100.0 / total);
    }

    private void populateFloors(DashboardStatistics stats) {
        for (FloorOccupancy floor : stats.floors()) {
            Span pill = floorPills.get(floor.floor());
            if (pill != null) {
                pill.setText(floor.occupiedBays() + " / " + floor.totalBays() + " OCCUPIED");
            }
        }
        bayTiles.clear();
        floorGrids.values().forEach(Div::removeAll);
        for (BayStatus bay : stats.bays()) {
            Div grid = floorGrids.get(bay.slot().floor());
            if (grid != null) {
                NativeButton tile = bayTile(bay);
                bayTiles.put(bay.slot().slotId(), tile);
                grid.add(tile);
            }
        }
    }

    private NativeButton bayTile(BayStatus bay) {
        ParkingSlot slot = bay.slot();
        ParkingSession session = bay.activeSession();
        Div top = box("bay-tile-top", text(slot.slotId(), "bay-id"));
        if (slot.charging()) {
            Span ev = new Span(Icons.bolt(11), text("EV", "bay-ev-tag"));
            ev.addClassName("bay-ev-badge");
            top.add(ev);
        }
        Span primary;
        Span secondary;
        if (bay.occupied()) {
            primary = text(Plates.display(session.registration()), "bay-plate");
            secondary = text(typeLabel(session.vehicleType()) + " · Occupied", "bay-sub");
        } else {
            primary = text("Available", "bay-available");
            secondary = text("Zone " + slot.zone().name() + " · Free", "bay-sub");
        }
        NativeButton tile = new NativeButton();
        tile.addClassNames("bay-tile", bay.occupied() ? "is-occupied" : "is-available");
        if (slot.charging()) {
            tile.addClassName("is-charging");
        }
        tile.add(top, primary, secondary);
        String status = bay.occupied() ? "Occupied" : "Available";
        StringBuilder label = new StringBuilder(slot.slotId()).append(", ").append(status);
        if (bay.occupied()) {
            label.append(", ").append(Plates.display(session.registration())).append(", ")
                    .append(session.vehicleType().displayName());
        }
        if (slot.charging()) {
            label.append(", EV charging bay");
        }
        tile.setAriaLabel(label.toString());
        tile.getElement().setAttribute("title", label.toString());
        boolean selected = slot.slotId().equals(selectedBayId);
        tile.getElement().setAttribute("aria-pressed", Boolean.toString(selected));
        if (selected) {
            tile.addClassName("is-selected");
        }
        tile.addClickListener(event -> selectBay(slot.slotId()));
        return tile;
    }

    private static String typeLabel(VehicleType type) {
        return type == VehicleType.BIKE ? "Bike" : "Car";
    }

    void selectBay(String slotId) {
        selectedBayId = slotId;
        bayTiles.forEach((id, tile) -> {
            boolean selected = id.equals(slotId);
            tile.getElement().setAttribute("aria-pressed", Boolean.toString(selected));
            if (selected) {
                tile.addClassName("is-selected");
            } else {
                tile.removeClassName("is-selected");
            }
        });
        showBayDetails(slotId);
    }

    private void showBayDetails(String slotId) {
        bayDetailSession.removeAll();
        BayStatus bay = lastStats == null || slotId == null ? null : lastStats.bays().stream()
                .filter(candidate -> candidate.slot().slotId().equals(slotId))
                .findFirst().orElse(null);
        bayDetailStatus.removeClassNames("is-free", "is-occupied");
        if (bay == null) {
            bayDetailTitle.setText("Select a bay");
            bayDetailStatus.setVisible(false);
            bayDetailMeta.setText("Choose any bay on the floor maps to inspect it.");
            return;
        }
        ParkingSlot slot = bay.slot();
        bayDetailTitle.setText(slot.slotId() + " · " + slot.floor().displayName());
        bayDetailStatus.setVisible(true);
        bayDetailStatus.setText(bay.occupied() ? "Occupied" : "Available");
        bayDetailStatus.addClassName(bay.occupied() ? "is-occupied" : "is-free");
        bayDetailMeta.setText("Zone " + slot.zone().name()
                + (slot.charging() ? " · Charging bay" : " · Standard bay"));
        if (bay.occupied()) {
            ParkingSession session = bay.activeSession();
            bayDetailSession.add(
                    text(Plates.display(session.registration()) + " · " + session.vehicleType().displayName(), "bay-detail-strong"),
                    text("Entered " + FormatUtil.dateTime(session.entryTime(), zone), "bay-detail-line"),
                    text(session.chargingRequested() ? "EV charging requested" : "EV charging not requested",
                            "bay-detail-line"));
        } else {
            bayDetailSession.add(text("No active vehicle. Ready for the next arrival.", "bay-detail-line"));
        }
    }

    private void populateGlance(DashboardStatistics stats) {
        carsSummary.setText(stats.carsEntered() + " entered · " + stats.carsExited() + " exited");
        bikesSummary.setText(stats.bikesEntered() + " entered · " + stats.bikesExited() + " exited");
        evUsage.setText(stats.chargingOccupied() + " / " + stats.chargingBays() + " in use");
        long pct = percent(stats.chargingOccupied(), stats.chargingBays());
        evBar.getStyle().set("width", pct + "%");
        evTrack.getElement().setAttribute("aria-valuemax", Integer.toString(stats.chargingBays()));
        evTrack.getElement().setAttribute("aria-valuenow", Integer.toString(stats.chargingOccupied()));
    }

    private void populateChart(DashboardStatistics stats) {
        chart.removeAll();
        long max = stats.entriesByHour().stream().mapToLong(HourlyEntries::entries).max().orElse(0);
        if (stats.peakHour().isEmpty() || max == 0) {
            peakValue.setText("—");
            peakHint.setText("No entries yet");
            chart.addClassName("is-empty");
            chart.getElement().setAttribute("aria-label", "No entries recorded yet.");
            chart.add(text("No entries recorded yet.", "empty-state"));
            return;
        }
        chart.removeClassName("is-empty");
        int peak = stats.peakHour().getAsInt();
        long peakEntries = stats.entriesByHour().get(peak).entries();
        peakValue.setText(FormatUtil.hourLabel(peak));
        peakHint.setText(peakEntries + (peakEntries == 1 ? " entry" : " entries"));
        chart.getElement().setAttribute("aria-label", "Vehicle entries by hour of day. Peak "
                + FormatUtil.hourLabel(peak) + " with " + peakHint.getText() + ".");

        Div bars = box("hour-bars");
        Div labels = box("hour-labels");
        labels.getElement().setAttribute("aria-hidden", "true");
        bars.getElement().setAttribute("aria-hidden", "true");
        for (HourlyEntries hour : stats.entriesByHour()) {
            Div bar = box("hour-bar");
            double height = hour.entries() == 0 ? 0 : Math.max(4, hour.entries() * 100.0 / max);
            bar.getStyle().set("height", String.format(Locale.ROOT, "%.1f%%", height));
            if (hour.hour() == peak) {
                bar.addClassName("is-peak");
            }
            Div column = box("hour-col", bar);
            if (hour.entries() == 0) {
                column.addClassName("is-zero");
            }
            column.getElement().setAttribute("title",
                    FormatUtil.hourLabel(hour.hour()) + ": " + hour.entries() + " entries");
            bars.add(column);
            if (hour.hour() % 3 == 0) {
                labels.add(text(shortHour(hour.hour()), "hour-label"));
            }
        }
        chart.add(bars, labels);
    }

    private static String shortHour(int hour) {
        int twelve = hour % 12 == 0 ? 12 : hour % 12;
        return twelve + (hour < 12 ? "a" : "p");
    }

    private void populateAlerts(List<LongStayAlert> alerts) {
        longStayCount.setText(Integer.toString(alerts.size()));
        longStayCount.getElement().setAttribute("aria-label", alerts.size() + " long-stay alerts");
        alertsList.removeAll();
        if (alerts.isEmpty()) {
            alertsList.add(text("No long-stay vehicles.", "empty-state"));
            return;
        }
        for (LongStayAlert alert : alerts) {
            Div row = box("alert-row",
                    box("alert-line", text(Plates.display(alert.registration()), "alert-plate"),
                            text("Long stay", "alert-tag"), box("snapshot-spacer"),
                            text(FormatUtil.duration(alert.elapsed()), "alert-duration")),
                    text(alert.slotId() + " · " + alert.floor().displayName() + " · "
                            + alert.vehicleType().displayName(), "alert-meta"),
                    text("Entered " + FormatUtil.dateTime(alert.entryTime(), zone), "alert-meta"));
            row.getElement().setAttribute("role", "listitem");
            alertsList.add(row);
        }
    }

    private void showMode(KioskMode mode) {
        pressed(entryModeButton, mode == KioskMode.ENTRY);
        pressed(exitModeButton, mode == KioskMode.EXIT);
    }

    private static void pressed(NativeButton button, boolean on) {
        button.getElement().setAttribute("aria-pressed", Boolean.toString(on));
        if (on) {
            button.addClassName("is-selected");
        } else {
            button.removeClassName("is-selected");
        }
    }

    // ---- actions ---------------------------------------------------------------------------

    void changeMode(KioskMode mode) {
        try {
            operations.changeKioskMode(mode);
        } catch (AdminAccessDeniedException denied) {
            toPinScreen();
            return;
        } catch (DataAccessException failure) {
            notice.show(InlineMessage.Kind.ERROR, "Could not save the kiosk mode. Please try again.");
            return;
        }
        showMode(mode);
        notice.show(InlineMessage.Kind.SUCCESS, mode == KioskMode.EXIT
                ? "Kiosk mode saved: Exit Kiosk. The public kiosk now opens Vehicle Exit."
                : "Kiosk mode saved: Entry Kiosk. The public kiosk now opens Vehicle Entry.");
    }

    void confirmReset() {
        try {
            operations.resetDemoData();
        } catch (AdminAccessDeniedException denied) {
            resetDialog.close();
            toPinScreen();
            return;
        } catch (DataAccessException failure) {
            resetDialog.close();
            notice.show(InlineMessage.Kind.ERROR, "Reset failed — nothing was changed.");
            return;
        }
        resetDialog.close();
        selectedBayId = null;
        refresh();
        notice.show(InlineMessage.Kind.SUCCESS,
                "Demo data reset. All bays are free, sessions and payments are cleared, and the kiosk is set to Entry.");
    }

    void lock() {
        authorization.lock();
        getUI().ifPresent(ui -> ui.navigate(KioskHomeView.class));
    }

    private void toPinScreen() {
        getUI().ifPresent(ui -> ui.navigate(AdminPinView.class));
    }
}
