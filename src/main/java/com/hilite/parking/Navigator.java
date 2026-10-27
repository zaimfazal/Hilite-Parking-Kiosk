package com.hilite.parking;

import com.hilite.parking.controller.AllocationController;
import com.hilite.parking.controller.ExitServiceAware;
import com.hilite.parking.controller.NavigatorAware;
import com.hilite.parking.controller.ServiceAware;
import com.hilite.parking.service.AllocationOutcome;
import com.hilite.parking.service.ExitService;
import com.hilite.parking.service.ParkingService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;

/**
 * Central screen switching. Keeps kiosk mode (Entry/Exit) as UI state only.
 *
 * <p>Screens are created by {@link FXMLLoader}, so this class hands them their collaborators
 * after loading: itself for navigation, the shared {@link ParkingService} for entry allocation
 * and the shared {@link ExitService} for the exit workflow. Nothing is stored globally.</p>
 */
public class Navigator {

    public enum KioskMode { ENTRY, EXIT }

    private enum View { ENTRY, EXIT, ALLOCATION, ADMIN_PIN, DASHBOARD }

    private final MainApp app;
    private final ParkingService parkingService;
    private final ExitService exitService;
    private KioskMode kioskMode = KioskMode.ENTRY;
    private View currentView = View.ENTRY;

    Navigator(MainApp app, ParkingService parkingService, ExitService exitService) {
        this.app = app;
        this.parkingService = parkingService;
        this.exitService = exitService;
    }

    public void showEntry() {
        kioskMode = KioskMode.ENTRY;
        currentView = View.ENTRY;
        load("/fxml/entry.fxml");
    }

    public void showExit() {
        kioskMode = KioskMode.EXIT;
        currentView = View.EXIT;
        load("/fxml/exit.fxml");
    }

    /** Shows the confirmation screen for a bay that has already been saved. */
    public void showAllocation(AllocationOutcome.Allocated allocation) {
        currentView = View.ALLOCATION;
        var loaded = load("/fxml/allocation.fxml");
        if (loaded.controller() instanceof AllocationController controller) {
            controller.setAllocation(allocation);
        }
    }

    public void showAdminPin() {
        currentView = View.ADMIN_PIN;
        load("/fxml/admin-pin.fxml");
    }

    public void showDashboard() {
        currentView = View.DASHBOARD;
        load("/fxml/dashboard.fxml");
    }

    public void showKiosk() {
        if (kioskMode == KioskMode.EXIT) {
            showExit();
        } else {
            showEntry();
        }
    }

    public void openAdminIfKiosk() {
        if (currentView != View.ADMIN_PIN && currentView != View.DASHBOARD) {
            showAdminPin();
        }
    }

    public KioskMode getKioskMode() {
        return kioskMode;
    }

    private LoadedView load(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource(fxmlPath));
            Parent root = loader.load();
            Object controller = loader.getController();
            if (controller instanceof NavigatorAware aware) {
                aware.setNavigator(this);
            }
            if (controller instanceof ServiceAware aware) {
                aware.setParkingService(parkingService);
            }
            if (controller instanceof ExitServiceAware aware) {
                aware.setExitService(exitService);
            }
            app.setRoot(root);
            return new LoadedView(root, controller);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load screen: " + fxmlPath, exception);
        }
    }

    private record LoadedView(Parent root, Object controller) {
    }
}
