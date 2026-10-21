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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void logsSuccessAndFailureWithoutTokenOrPrivateMaterial() throws Exception {
        var keys = TokenTestResource.keyPair();
        String privateMaterial = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
        String privatePem = TokenTestResource.pem("PRIVATE KEY", keys.getPrivate().getEncoded());
        String[] issuedToken = {null};
        var hasher = new PasswordHasher();
        var credentials = new LegacyDatabase.StoredCredentials(
                new UserRecord(42, "fixture", "Fixture", "STUDENT"), hasher.hash("test-only"));
        AuthResource resource = new AuthResource();
        resource.service = new AuthService(new LegacyDatabase() {
            @Override
            public StoredCredentials findByUsername(String username) {
                if ("explode".equals(username)) {
                    var error = new IllegalStateException(issuedToken[0] + privatePem,
                            new IllegalArgumentException(privateMaterial));
                    error.addSuppressed(new IllegalArgumentException(issuedToken[0]));
                    throw error;
                }
                return credentials;
            }
        }, hasher, new TokenService(keys.getPrivate(), TokenTestResource.ISSUER,
                TokenTestResource.AUDIENCE, java.time.Clock.systemUTC()));
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        Logger logger = Logger.getLogger("com.bcp.workshop.auth");
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
            LoginRequest request = new LoginRequest();
            request.username = "fixture";
            request.password = "test-only";
            request.client = "Web_A-01";
            try (var response = resource.login(request)) {
                assertEquals(200, response.getStatus());
                issuedToken[0] = ((LoginResponse) response.getEntity()).token();
            }
            request.password = "incorrect";
            try (var response = resource.login(request)) {
                assertEquals(401, response.getStatus());
            }
            request.client = " invalid ";
            try (var response = resource.login(request)) {
                assertEquals(400, response.getStatus());
            }
            assertEquals(0, records.size());
            request.client = "Web_A-01";
            request.username = "explode";
            try (var response = resource.login(request)) {
                assertEquals(500, response.getStatus());
                assertEquals(Map.of("message", "Internal server error", "exception",
                        "InternalServerError"), response.getEntity());
            }
            assertEquals(1, records.size());
            assertTrue(records.getFirst().getMessage().contains(IllegalStateException.class.getName()));
            var formatter = new SimpleFormatter();
            var rendered = new StringBuilder();
            records.forEach(record -> rendered.append(formatter.format(record)));
            assertFalse(rendered.toString().contains(issuedToken[0]));
            assertFalse(rendered.toString().contains(privatePem));
            assertFalse(rendered.toString().contains(privateMaterial));
            assertFalse(rendered.toString().contains("-----BEGIN PRIVATE KEY-----"));
        } finally {
            logger.removeHandler(capture);
        }
    }

    @Test
    void rejectsMissingClientOverHttp() {
        given().contentType("application/json")
                .body(Map.of("username", "student", "password", "Workshop123!"))
                .when().post("/auth/login").then().statusCode(400)
                .body("$", aMapWithSize(1)).body("message", is("Invalid client"));
    }

    @ParameterizedTest
    @MethodSource("invalidClients")
    void rejectsInvalidClientBeforeCredentials(String client) {
        Map<String, Object> request = new HashMap<>();
        request.put("username", "student");
        request.put("password", "incorrect");
        request.put("client", client);
        given().contentType("application/json").body(request)
                .when().post("/auth/login").then().statusCode(400)
                .body("$", aMapWithSize(1)).body("message", is("Invalid client"));
    }

    static Stream<String> invalidClients() {
        return Stream.of(null, "", " ", " client", "client ", "a b", "_client", "-client",
                "a.b", "a/b", "cli\u00e9nt", "a\n", "a".repeat(65));
    }

    @ParameterizedTest
    @MethodSource("nonStringClients")
    void rejectsNonStringClient(Object client) {
        given().contentType("application/json")
                .body(Map.of("username", "student", "password", "Workshop123!", "client", client))
                .when().post("/auth/login").then().statusCode(400)
                .body("$", aMapWithSize(1)).body("message", is("Invalid client"));
    }

    static Stream<Object> nonStringClients() {
        return Stream.of(42, true, List.of("web"), Map.of("name", "web"));
    }

    @ParameterizedTest
    @MethodSource("validClients")
    void preservesExactClientAtAcceptedBoundaries(String client) throws Exception {
        String token = given().contentType("application/json")
                .body(Map.of("username", "student", "password", "Workshop123!", "client", client))
                .when().post("/auth/login").then().statusCode(200).extract().path("token");
        var claims = org.jose4j.jwt.JwtClaims.parse(new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8));
        assertEquals(client, claims.getStringClaimValue("client"));
    }

    static Stream<String> validClients() {
        return Stream.of("A", "7", "Web_A-01", "a".repeat(64));
    }

    @Test
    void doesNotCallServiceForInvalidClient() {
        AuthResource resource = new AuthResource();
        resource.service = new AuthService(null, null, null) {
            @Override
            public LoginResponse authenticate(String username, String password, String client) {
                throw new AssertionError("Invalid clients must not reach authentication");
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "fixture";
        request.password = "incorrect";
        request.client = " invalid ";
        try (var response = resource.login(request)) {
            assertEquals(400, response.getStatus());
            assertEquals(Map.of("message", "Invalid client"), response.getEntity());
        }
    }

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
    void preservesSuccessfulResponseWithApprovedClient(String username, String password, String displayName, String role)
            throws Exception {
        long before = Instant.now().getEpochSecond();
        String token = given()
                .contentType("application/json")
                .body(Map.of("username", username, "password", password, "client", "Web_A-01", "ignored", true))
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
        var claims = org.jose4j.jwt.JwtClaims.parse(
            new String(Base64.getUrlDecoder().decode(fields[1]), StandardCharsets.UTF_8));
        assertEquals(username, claims.getSubject());
        assertEquals(role, claims.getStringClaimValue("role"));
        assertEquals("Web_A-01", claims.getStringClaimValue("client"));
        assertEquals(TokenTestResource.ISSUER, claims.getIssuer());
        assertEquals(List.of(TokenTestResource.AUDIENCE), claims.getAudience());
        long issuedAt = claims.getIssuedAt().getValue();
        assertTrue(issuedAt >= before && issuedAt <= after);
        long expiry = claims.getExpirationTime().getValue();
        assertEquals(3600L, expiry - issuedAt);
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
                .body(Map.of("username", username, "password", password, "client", "Web_A-01"))
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
            public LoginResponse authenticate(String username, String password, String client) {
                throw new IllegalStateException(message);
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "fixture";
        request.password = "test-only";
        request.client = "Web_A-01";

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
        request.client = "Web_A-01";

        try (var response = resource.login(request)) {
            assertEquals(200, response.getStatus());
            assertTrue(response.getEntity() instanceof LoginResponse);
            LoginResponse body = (LoginResponse) response.getEntity();
            assertEquals(new LoginResponse.User("fixture", expected, "CUSTOM"), body.user());
        }
    }
}
