package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(AuthFailureTest.FailureProfile.class)
class AuthFailureTest {
    private static final String SENSITIVE_MARKER = "fixture-password-hash-token-key";

    @ParameterizedTest
    @ValueSource(strings = {"with-message", "without-message", "sensitive-message"})
    void returnsGenericInternalErrorOverHttp(String username) {
        given()
                .contentType("application/json")
                .body(Map.of("username", username, "password", "test-only", "client", "Web_A-01"))
                .when().post("/auth/login")
                .then()
                .statusCode(500)
                .contentType("application/json")
                .body("$", aMapWithSize(2))
                .body("message", is("Internal server error"))
                .body("exception", is("InternalServerError"));
    }

    @Test
    void logsDiagnosticWithoutSensitiveValues() {
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        Logger logger = Logger.getLogger(AuthResource.class.getName());
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(capture);
        try {
            given().contentType("application/json")
                    .body(Map.of("username", "sensitive-message", "password", SENSITIVE_MARKER, "client", "Web_A-01"))
                    .when().post("/auth/login").then().statusCode(500);
            assertEquals(1, records.size());
            LogRecord record = records.getFirst();
            assertTrue(record.getMessage().contains(IllegalStateException.class.getName()));
            assertNotNull(record.getThrown());
            assertTrue(List.of(record.getThrown().getStackTrace()).stream()
                    .anyMatch(frame -> frame.getMethodName().equals("authenticate")));
            var rendered = new java.io.StringWriter();
            record.getThrown().printStackTrace(new java.io.PrintWriter(rendered));
            assertFalse((record.getMessage() + rendered).contains(SENSITIVE_MARKER));
            assertNull(record.getThrown().getCause());
            assertEquals(0, record.getThrown().getSuppressed().length);
        } finally {
            logger.removeHandler(capture);
        }
    }

    public static class FailureProfile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FailingService.class);
        }
    }

    @Alternative
    @ApplicationScoped
    public static class FailingService extends AuthService {
        public FailingService() {
            super(null, null, null);
        }

        @Override
        public LoginResponse authenticate(String username, String password, String client) {
            if ("sensitive-message".equals(username)) {
                var error = new IllegalStateException(SENSITIVE_MARKER,
                        new IllegalArgumentException(SENSITIVE_MARKER));
                error.addSuppressed(new IllegalArgumentException(SENSITIVE_MARKER));
                throw error;
            }
            throw new IllegalStateException("with-message".equals(username) ? "isolated failure" : null);
        }
    }
}
