package com.hilite.parking.deploy;

import com.hilite.parking.config.HiliteProperties;
import com.hilite.parking.model.KioskMode;
import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.repository.ParkingSlotRepository;
import com.hilite.parking.service.AdminAccessService;
import com.hilite.parking.web.security.AdminAuthorization;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The application starts against a configured database file (as {@code HILITE_DB_PATH} does in
 * Docker), creates and seeds it, binds the {@code hilite.*} settings, and refuses to start with
 * an invalid admin setting.
 */
@SpringBootTest
class ApplicationStartupTest {

    @TempDir
    static Path tempDir;

    /** Whether ./data existed before this class started any context. */
    private static boolean defaultDataDirExistedBefore;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // A not-yet-existing sub-directory: startup must create it, like /data on a fresh volume.
        registry.add("hilite.db-path", () -> dbFile().toString());
        registry.add("hilite.time-zone", () -> "Europe/London");
    }

    private static Path dbFile() {
        return tempDir.resolve("volume").resolve("hilite-parking.db");
    }

    @BeforeAll
    static void recordDefaultDataDirectory() {
        defaultDataDirExistedBefore = Files.exists(Path.of("data"));
    }

    @Autowired
    Database database;

    @Autowired
    ParkingSlotRepository slots;

    @Autowired
    ParkingSessionRepository sessions;

    @Autowired
    AdminAccessService adminAccess;

    @Autowired
    ZoneId mallTimeZone;

    @Autowired
    HiliteProperties properties;

    @Autowired
    AdminAuthorization adminAuthorization;

    @Test
    void startupUsesConfiguredFileAndSeedsTheLayout() {
        assertThat(database.file()).isEqualTo(dbFile());
        assertThat(dbFile()).isRegularFile();
        assertThat(slots.count()).isEqualTo(24L);
        assertThat(slots.findAll()).hasSize(24);
        assertThat(slots.findCharging()).hasSize(4);
        assertThat(sessions.countEntries()).isZero();
        assertThat(adminAccess.currentMode()).isEqualTo(KioskMode.ENTRY);
        assertThat(adminAccess.verifyPin("1234")).isTrue();
        assertThat(adminAuthorization).isNotNull();
    }

    @Test
    void startupDoesNotCreateTheDefaultDataDirectory() {
        if (!defaultDataDirExistedBefore) {
            assertThat(Path.of("data")).doesNotExist();
        }
        assertThat(Path.of("data", "hilite-parking.db").toAbsolutePath())
                .isNotEqualTo(database.file().toAbsolutePath());
    }

    @Test
    void timeZoneIsBoundFromConfiguration() {
        assertThat(properties.timeZone()).isEqualTo(ZoneId.of("Europe/London"));
        assertThat(mallTimeZone).isEqualTo(ZoneId.of("Europe/London"));
        // Untouched settings keep their documented defaults.
        assertThat(properties.adminMaxAttempts()).isEqualTo(5);
        assertThat(properties.adminLockout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void invalidAdminMaxAttemptsFailsTheWholeApplicationStartup() {
        Path otherFile = tempDir.resolve("invalid").resolve("hilite-parking.db");

        assertThatThrownBy(() -> DeployTestSupport.start(otherFile, null,
                        Map.of("hilite.admin-max-attempts", "0")).close())
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("hilite.admin-max-attempts must be at least 1");
    }

    @Test
    void invalidAdminPropertiesAreRejectedWhenBinding() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(PropertiesOnly.class);

        runner.withPropertyValues("hilite.admin-max-attempts=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("hilite.admin-max-attempts=-3")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("hilite.admin-lockout=-5s")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("hilite.time-zone=Not/AZone")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("hilite.admin-max-attempts=3", "hilite.admin-lockout=2m",
                        "hilite.time-zone=UTC", "hilite.db-path=/data/hilite-parking.db")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    HiliteProperties bound = context.getBean(HiliteProperties.class);
                    assertThat(bound.adminMaxAttempts()).isEqualTo(3);
                    assertThat(bound.adminLockout()).isEqualTo(Duration.ofMinutes(2));
                    assertThat(bound.timeZone()).isEqualTo(ZoneId.of("UTC"));
                    assertThat(bound.dbPath()).isEqualTo(Path.of("/data/hilite-parking.db"));
                });
    }

    /** Binds only {@link HiliteProperties}, without touching any database. */
    @EnableConfigurationProperties(HiliteProperties.class)
    static class PropertiesOnly {
    }
}
