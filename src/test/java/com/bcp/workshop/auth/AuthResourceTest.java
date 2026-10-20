package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@QuarkusTestResource(TokenTestResource.class)
class AuthResourceTest {
    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    @ParameterizedTest
    @CsvSource({
            "student, Workshop123!, Workshop Student, STUDENT",
            "trainer, Trainer123!, Workshop Trainer, TRAINER"
    })
    void characterizesSuccessfulResponse(String username, String password, String displayName, String role) {
        long before = Instant.now().getEpochSecond();
        String token = given()
                .contentType("application/json")
                .body(Map.of("username", username, "password", password, "ignored", true))
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$", aMapWithSize(4))
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"))
                .body("user", aMapWithSize(3))
                .body("user.username", is(username))
                .body("user.displayName", is(displayName))
                .body("user.role", is(role))
                .extract().path("token");
        long after = Instant.now().getEpochSecond();
        String[] fields = token.split("\\.");
        assertEquals(3, fields.length);
        Map<String, Object> claims = io.restassured.path.json.JsonPath.from(
                new String(Base64.getUrlDecoder().decode(fields[1]), StandardCharsets.UTF_8)).getMap("$");
        assertEquals(username, claims.get("sub"));
        assertEquals(role, claims.get("role"));
        long expiry = ((Number) claims.get("exp")).longValue();
        assertTrue(expiry >= before + 3600 && expiry <= after + 3600);
    }

    @ParameterizedTest
    @CsvSource({
            "student, incorrect",
            "unknown-user, Workshop123!",
            "' student ', Workshop123!",
            "student, ' Workshop123! '"
    })
    void characterizesUnauthorizedResponse(String username, String password) {
        given()
                .contentType("application/json")
                .body(Map.of("username", username, "password", password))
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("$", aMapWithSize(1))
                .body("message", is("Invalid user " + username + " or password"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "{}; Username is required",
            "{\"username\":null}; Username is required",
            "{\"username\":\"\"}; Username is required",
            "{\"username\":\"   \"}; Username is required",
            "{\"username\":\"student\"}; Password is required",
            "{\"username\":\"student\",\"password\":null}; Password is required",
            "{\"username\":\"student\",\"password\":\"\"}; Password is required",
            "{\"username\":\"student\",\"password\":\"   \"}; Password is required"
    })
    void characterizesValidationResponse(String body, String message) {
        given()
                .contentType("application/json")
                .body(body)
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("$", aMapWithSize(1))
                .body("message", is(message));
    }

    @Test
    void characterizesNullRequest() {
        try (var response = new AuthResource().login(null)) {
            assertEquals(400, response.getStatus());
            assertEquals(Map.of("message", "Request is null"), response.getEntity());
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "isolated failure")
    void characterizesResourceException(String message) {
        AuthResource resource = new AuthResource();
        resource.service = new AuthService(null, null, null) {
            @Override
            public LoginResponse authenticate(String username, String password) {
                throw new IllegalStateException(message);
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "fixture";
        request.password = "test-only";

        try (var response = resource.login(request)) {
            assertEquals(500, response.getStatus());
            assertEquals(Map.of("message", "Internal server error", "exception",
                    "InternalServerError"), response.getEntity());
        }
    }

    @Test
    void rejectsJsonNullOverHttp() {
        given()
                .contentType("application/json")
                .body("null")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("$", aMapWithSize(1))
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsMalformedJson() {
        given()
                .contentType("application/json")
                .body("{")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body(emptyString());
    }

    @Test
    void rejectsIncompatibleFieldType() {
        given()
                .contentType("application/json")
                .body("{\"username\":{},\"password\":\"x\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("$", aMapWithSize(5))
                .body("objectName", is("LoginRequest"))
                .body("attributeName", is("username"))
                .body("line", is(1))
                .body("column", is(13))
                .body("value", nullValue());
    }

    @Test
    void rejectsUnsupportedMethod() {
        given()
                .when().get("/auth/login")
                .then()
                .statusCode(405)
                .body(emptyString());
    }

    @Test
    void rejectsIncompatibleAccept() {
        given()
                .contentType("application/json")
                .accept("text/plain")
                .body("{}")
                .when().post("/auth/login")
                .then()
                .statusCode(406)
                .body(emptyString());
    }

    @Test
    void rejectsUnsupportedContentType() {
        given()
                .contentType("text/plain")
                .body("test")
                .when().post("/auth/login")
                .then()
                .statusCode(415)
                .body(emptyString());
    }

    @ParameterizedTest
    @MethodSource("com.bcp.workshop.auth.AuthServiceTest#displayNames")
    void escapesDisplayNameWithoutExposingPersistenceFields(String displayName, String expected) {
        AuthResource resource = new AuthResource();
        PasswordHasher hasher = new PasswordHasher();
        String hash = hasher.hash("test-only");
        resource.service = new AuthService(new LegacyDatabase() {
            @Override
            public StoredCredentials findByUsername(String username) {
                return new StoredCredentials(
                        new UserRecord(42, username, displayName, "CUSTOM"), hash);
            }
        }, hasher, TokenTestResource.service());
        LoginRequest request = new LoginRequest();
        request.username = "fixture";
        request.password = "test-only";

        try (var response = resource.login(request)) {
            assertEquals(200, response.getStatus());
            assertTrue(response.getEntity() instanceof LoginResponse);
            LoginResponse body = (LoginResponse) response.getEntity();
            assertEquals(new LoginResponse.User("fixture", expected, "CUSTOM"), body.user());
        }
    }
}
