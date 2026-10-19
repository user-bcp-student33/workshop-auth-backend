package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(HealthDownTest.DownProfile.class)
class HealthDownTest {
    @Test
    void returnsServiceUnavailableWhenHealthIsDown() {
        given()
                .accept("application/json")
                .when().get("/q/health")
                .then()
                .statusCode(503);
    }

    public static class DownProfile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(UnavailableHealthCheck.class);
        }
    }

    @Alternative
    @ApplicationScoped
    @Readiness
    public static class UnavailableHealthCheck implements HealthCheck {
        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.down("test-only-unavailable");
        }
    }
}
