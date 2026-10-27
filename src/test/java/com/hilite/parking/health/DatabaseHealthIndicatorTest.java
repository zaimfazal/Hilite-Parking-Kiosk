package com.hilite.parking.health;

import com.hilite.parking.repository.Database;
import com.hilite.parking.repository.DatabaseInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link DatabaseHealthIndicator}; no Spring context. */
class DatabaseHealthIndicatorTest {

    @TempDir
    Path tempDir;

    @Test
    void initializedDatabaseIsUp() {
        Database database = new Database(tempDir.resolve("ok.db"));
        new DatabaseInitializer(database).initialize();

        Health health = new DatabaseHealthIndicator(database).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).isEmpty();
    }

    @Test
    void fileWithoutTablesIsDown() throws Exception {
        Path file = tempDir.resolve("empty.db");
        Database database = new Database(file);
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE unrelated (id INTEGER)");
        }

        assertGenericDown(new DatabaseHealthIndicator(database).health(), file);
    }

    @Test
    void missingSingleTableIsDown() throws Exception {
        Path file = tempDir.resolve("partial.db");
        Database database = new Database(file);
        new DatabaseInitializer(database).initialize();
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = OFF");
            statement.execute("DROP TABLE payments");
        }

        assertGenericDown(new DatabaseHealthIndicator(database).health(), file);
    }

    @Test
    void unusablePathIsDown() {
        // The parent directory does not exist and the indicator must not create it.
        Path file = tempDir.resolve("no-such-directory").resolve("hilite-parking.db");

        assertGenericDown(new DatabaseHealthIndicator(new Database(file)).health(), file);
        assertThat(file.getParent()).doesNotExist();
    }

    @Test
    void pathThatIsADirectoryIsDown() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("a-directory.db"));

        assertGenericDown(new DatabaseHealthIndicator(new Database(directory)).health(),
                directory);
    }

    private static void assertGenericDown(Health health, Path file) {
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsExactly(Map.entry("error", DatabaseHealthIndicator.GENERIC_FAILURE));
        String rendered = health.toString();
        assertThat(rendered)
                .doesNotContain(file.toString())
                .doesNotContain(file.getFileName().toString())
                .doesNotContainIgnoringCase("select")
                .doesNotContainIgnoringCase("sqlite")
                .doesNotContainIgnoringCase("exception")
                .doesNotContain("1234");
    }
}
