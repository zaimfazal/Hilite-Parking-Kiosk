package com.hilite.parking.deploy;

import com.hilite.parking.HiliteParkingApplication;
import com.hilite.parking.repository.Database;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Starts and stops the real application context outside the Spring TestContext framework, so a
 * test can close a context and start a fresh one on the same database file (a "restart").
 */
final class DeployTestSupport {

    private DeployTestSupport() {
    }

    /**
     * Starts the full application (all beans, embedded server on a random port) on {@code dbFile}.
     *
     * @param primaryClock when non-null, registered as the {@code @Primary} {@link Clock}
     * @param extra        additional properties (may override the defaults below)
     */
    static ConfigurableApplicationContext start(Path dbFile, Clock primaryClock,
                                                Map<String, Object> extra) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("hilite.db-path", dbFile.toString());
        properties.put("spring.main.banner-mode", "off");
        // Vaadin's auto-configuration needs a servlet web application; use a random free port.
        properties.put("server.port", "0");
        properties.putAll(extra);

        // Passed as command-line arguments: they outrank application.properties (builder
        // "properties(...)" are only defaults and would silently lose to
        // hilite.db-path=data/hilite-parking.db).
        String[] args = properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);

        ConfigurableApplicationContext context = new SpringApplicationBuilder(HiliteParkingApplication.class)
                .web(WebApplicationType.SERVLET)
                .initializers(initializing -> {
                    // A plain SpringApplication run has no TestTypeExcludeFilter, so keep
                    // configuration classes from src/test (other tests' helpers) out of the scan.
                    initializing.getBeanFactory().registerSingleton(
                            "testClassesExcludeFilter", new TestClassesExcludeFilter());
                    if (primaryClock != null) {
                        ((GenericApplicationContext) initializing).registerBean("testClock",
                                Clock.class, () -> primaryClock, bd -> bd.setPrimary(true));
                    }
                })
                .run(args);
        Path used = context.getBean(Database.class).file();
        if (!used.equals(dbFile)) {
            context.close();
            throw new IllegalStateException("test context ignored the temporary database: " + used);
        }
        return context;
    }

    /** Excludes every class compiled from {@code src/test} from component scanning. */
    static final class TestClassesExcludeFilter extends TypeExcludeFilter {

        @Override
        public boolean match(MetadataReader reader, MetadataReaderFactory factory)
                throws IOException {
            String location = reader.getResource().getURL().toString().replace('\\', '/');
            return location.contains("/test-classes/");
        }

        @Override
        public boolean equals(Object other) {
            return other != null && other.getClass() == getClass();
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }
    }
}
