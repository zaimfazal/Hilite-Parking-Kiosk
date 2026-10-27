package com.hilite.parking.config;

import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.DatabaseInitializer;
import com.hilite.parking.repository.ParkingSessionRepository;
import com.hilite.parking.repository.ParkingSlotRepository;
import com.hilite.parking.repository.SettingsRepository;
import com.hilite.parking.repository.jdbc.JdbcExitCompletionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSessionRepository;
import com.hilite.parking.repository.jdbc.JdbcParkingSlotRepository;
import com.hilite.parking.repository.jdbc.JdbcSettingsRepository;
import com.hilite.parking.service.AdminAccessService;
import com.hilite.parking.service.DemoResetService;
import com.hilite.parking.service.ExitService;
import com.hilite.parking.service.FeeCalculator;
import com.hilite.parking.service.LongStayService;
import com.hilite.parking.service.ParkingService;
import com.hilite.parking.service.StatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Explicit wiring of the existing Java business layer.
 *
 * <p>The same constructors the JavaFX build used are called once here, so every browser session
 * shares one set of stateless services over one SQLite file. The database is initialized while
 * the {@link DatabaseInitializer} bean is created, before any repository exists: no view or
 * request can reach an uninitialized file, and a broken file stops the application at startup
 * instead of failing per request. Startup never deletes or repairs data; only the admin reset
 * clears records.</p>
 */
@Configuration(proxyBeanMethods = false)
public class ParkingConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ParkingConfiguration.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ZoneId mallTimeZone(HiliteProperties properties) {
        return properties.timeZone();
    }

    @Bean
    Database database(HiliteProperties properties) {
        Database database = new Database(properties.dbPath());
        LOG.info("Using SQLite database {}", database.file().toAbsolutePath());
        return database;
    }

    @Bean
    DatabaseInitializer databaseInitializer(Database database) {
        DatabaseInitializer initializer = new DatabaseInitializer(database);
        initializer.initialize();
        return initializer;
    }

    @Bean
    ParkingSlotRepository parkingSlotRepository(Database database,
                                                DatabaseInitializer initialized) {
        return new JdbcParkingSlotRepository(database);
    }

    @Bean
    ParkingSessionRepository parkingSessionRepository(Database database,
                                                      DatabaseInitializer initialized) {
        return new JdbcParkingSessionRepository(database);
    }

    @Bean
    SettingsRepository settingsRepository(Database database, DatabaseInitializer initialized) {
        return new JdbcSettingsRepository(database);
    }

    @Bean
    FeeCalculator feeCalculator() {
        return new FeeCalculator();
    }

    @Bean
    ParkingService parkingService(ParkingSlotRepository slots, ParkingSessionRepository sessions,
                                  Clock clock) {
        return new ParkingService(slots, sessions, clock);
    }

    @Bean
    ExitService exitService(ParkingSlotRepository slots, ParkingSessionRepository sessions,
                            Database database, DatabaseInitializer initialized,
                            FeeCalculator feeCalculator, Clock clock) {
        return new ExitService(slots, sessions, new JdbcExitCompletionRepository(database),
                feeCalculator, clock);
    }

    @Bean
    AdminAccessService adminAccessService(SettingsRepository settings) {
        return new AdminAccessService(settings);
    }

    @Bean
    StatisticsService statisticsService(ParkingSlotRepository slots,
                                        ParkingSessionRepository sessions, ZoneId mallTimeZone) {
        return new StatisticsService(slots, sessions, mallTimeZone);
    }

    @Bean
    LongStayService longStayService(ParkingSlotRepository slots,
                                    ParkingSessionRepository sessions, Clock clock) {
        return new LongStayService(slots, sessions, clock);
    }

    @Bean
    DemoResetService demoResetService(DatabaseInitializer databaseInitializer) {
        return new DemoResetService(databaseInitializer);
    }
}
