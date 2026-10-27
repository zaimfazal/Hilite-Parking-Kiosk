package com.hilite.parking.deploy;

import com.hilite.parking.health.DatabaseHealthIndicator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** The deployment health check (Docker HEALTHCHECK, platforms) over real HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("hilite.db-path", () -> tempDir.resolve("hilite-parking.db").toString());
    }

    @Autowired
    Environment environment;

    @Autowired
    DatabaseHealthIndicator databaseHealthIndicator;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Test
    void healthEndpointIsUpWithoutDetails() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        // show-details=never: no components, no file path, no PIN.
        assertThat(response.body())
                .doesNotContain("components")
                .doesNotContain("details")
                .doesNotContain("hilite-parking.db")
                .doesNotContain("1234");
    }

    @Test
    void databaseIndicatorIsRegisteredAndUp() {
        assertThat(databaseHealthIndicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        // Unknown paths fall through to the Vaadin servlet (an HTML app page), so assert that
        // no actuator payload is served rather than a particular status code.
        for (String path : new String[] {"/actuator/env", "/actuator/beans",
                "/actuator/configprops", "/actuator/heapdump"}) {
            HttpResponse<String> response = get(path);
            assertThat(response.headers().firstValue("Content-Type").orElse(""))
                    .as(path).doesNotContain("actuator").doesNotContain("json")
                    .doesNotContain("octet-stream");
            assertThat(response.body()).as(path)
                    .doesNotContain("propertySources")
                    .doesNotContain("hilite.db-path")
                    .doesNotContain("\"beans\"")
                    .doesNotContain("\"contexts\"");
        }
        // The index lists only the health endpoint.
        HttpResponse<String> index = get("/actuator");
        assertThat(index.statusCode()).isEqualTo(200);
        assertThat(index.body()).contains("/actuator/health").doesNotContain("/actuator/env");
    }

    private HttpResponse<String> get(String path) throws Exception {
        String port = environment.getRequiredProperty("local.server.port");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
