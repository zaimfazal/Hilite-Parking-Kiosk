package com.hilite.parking;

import com.hilite.parking.repository.DataAccessException;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.DatabaseInitializer;
import com.hilite.parking.repository.jdbc.JdbcExitCompletionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSlotRepository;
import com.hilite.parking.service.ExitService;
import com.hilite.parking.service.FeeCalculator;
import com.hilite.parking.service.ParkingService;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * JavaFX entry point and screen host.
 *
 * <p>Startup order matters: the SQLite file is initialized once, before any screen exists, so
 * the entry kiosk never shows placeholder data and then fails on the first real allocation. If
 * initialization fails, the window shows a readable error screen instead of the kiosk.</p>
 *
 * <p>The database location can be overridden for an isolated demo or a manual test run with
 * {@code -Dhilite.db=<path>}. Without the property the application uses
 * {@link Database#DEFAULT_FILE} ({@code data/hilite-parking.db}).</p>
 */
public class MainApp extends Application {

    /** Optional database file override; see the class comment. */
    public static final String DATABASE_PROPERTY = "hilite.db";

    private static final double DESIGN_WIDTH = 1600;
    private static final double DESIGN_HEIGHT = 900;

    private Stage stage;
    private Scene scene;
    private Navigator navigator;
    private String densityClass = "";

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;

        scene = new Scene(new StackPane(), DESIGN_WIDTH, DESIGN_HEIGHT);
        scene.getStylesheets().addAll(
                stylesheet("/css/app.css"),
                stylesheet("/css/kiosk.css"),
                stylesheet("/css/admin.css")
        );
        scene.widthProperty().addListener((obs, oldValue, value) -> applyDensity());
        scene.heightProperty().addListener((obs, oldValue, value) -> applyDensity());

        stage.setTitle("HiLITE Mall Smart Parking");
        stage.setScene(scene);
        stage.setMinWidth(1280);
        stage.setMinHeight(720);
        stage.setWidth(DESIGN_WIDTH);
        stage.setHeight(DESIGN_HEIGHT);
        stage.centerOnScreen();

        Database database = configuredDatabase();
        try {
            new DatabaseInitializer(database).initialize();
        } catch (DataAccessException failure) {
            showStartupFailure(database, failure);
            stage.show();
            return;
        }

        JdbcParkingSlotRepository slotRepository = new JdbcParkingSlotRepository(database);
        JdbcParkingSessionRepository sessionRepository = new JdbcParkingSessionRepository(database);

        ParkingService parkingService = new ParkingService(slotRepository, sessionRepository);
        ExitService exitService = new ExitService(
                slotRepository,
                sessionRepository,
                new JdbcExitCompletionRepository(database),
                new FeeCalculator());
        navigator = new Navigator(this, parkingService, exitService);

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                navigator::openAdminIfKiosk
        );

        navigator.showEntry();
        stage.show();
        applyDensity();
    }

    /** Resolves the database file, honouring the documented demo/test override. */
    private static Database configuredDatabase() {
        String override = System.getProperty(DATABASE_PROPERTY);
        if (override == null || override.isBlank()) {
            return Database.defaultDatabase();
        }
        return new Database(Path.of(override.trim()));
    }

    /**
     * Replaces the kiosk with a readable failure card. Nothing is retried automatically and no
     * preview or placeholder session is shown, so a broken database cannot look like a working
     * kiosk.
     */
    private void showStartupFailure(Database database, DataAccessException failure) {
        Label brand = new Label("HiLITE MALL  ·  SMART PARKING");
        brand.setStyle("-fx-text-fill: #9AA6B2; -fx-font-size: 12px; -fx-letter-spacing: 2px;");

        Label title = new Label("Parking data could not be started");
        title.setStyle("-fx-text-fill: #F4F7FA; -fx-font-size: 30px; -fx-font-weight: bold;");

        Label summary = new Label(
                "The local parking database could not be opened or created, so the kiosk was "
                        + "not started. No parking session was created.");
        summary.setWrapText(true);
        summary.setStyle("-fx-text-fill: #C7D0DA; -fx-font-size: 15px;");

        Label details = new Label("Database: " + database.file().toAbsolutePath()
                + "\n\n" + describe(failure));
        details.setWrapText(true);
        details.setStyle("-fx-text-fill: #FFB4A2; -fx-font-size: 13px;"
                + " -fx-font-family: 'Consolas', monospace;");

        Label hint = new Label(
                "Close the application, make sure the data folder is writable and not corrupted, "
                        + "then start again. Existing records were not deleted.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #9AA6B2; -fx-font-size: 13px;");

        VBox card = new VBox(16, brand, title, summary, details, hint);
        card.setMaxWidth(760);
        card.setPadding(new Insets(36, 40, 36, 40));
        card.setStyle("-fx-background-color: rgba(18, 24, 32, 0.94);"
                + " -fx-background-radius: 18; -fx-border-radius: 18;"
                + " -fx-border-color: rgba(255, 255, 255, 0.10);");

        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%,"
                + " #0B1016, #16202B);");
        root.setPadding(new Insets(40));
        scene.setRoot(root);
    }

    private static String describe(Throwable failure) {
        StringBuilder text = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (!text.isEmpty()) {
                text.append("\nCaused by: ");
            }
            text.append(current.getMessage() == null
                    ? current.getClass().getSimpleName()
                    : current.getMessage());
            current = current.getCause();
        }
        return text.toString();
    }

    private String stylesheet(String resource) {
        var url = MainApp.class.getResource(resource);
        if (url == null) {
            throw new IllegalStateException("Stylesheet not found: " + resource);
        }
        return url.toExternalForm();
    }

    void setRoot(Parent root) {
        root.getStyleClass().removeAll("compact", "wide");
        if (!densityClass.isEmpty()) {
            root.getStyleClass().add(densityClass);
        }
        scene.setRoot(root);
        applyDensity();
    }

    /**
     * Keeps the layout readable below the 1920x1080 design size by toggling a compact style class.
     */
    private void applyDensity() {
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        String wanted = scene.getWidth() < 1500 || scene.getHeight() < 820 ? "compact" : "wide";
        if (!wanted.equals(densityClass)) {
            densityClass = wanted;
        }
        var root = scene.getRoot();
        root.getStyleClass().removeAll("compact", "wide");
        root.getStyleClass().add(wanted);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
