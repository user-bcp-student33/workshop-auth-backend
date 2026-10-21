package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    @Test
    void responseLifetimeMatchesSignedExpirationAtIssuance() throws Exception {
    Instant now = Instant.parse("2026-10-21T12:00:00.987654321Z");
    var keys = TokenTestResource.keyPair();
    var hasher = new PasswordHasher();
    var credentials = new LegacyDatabase.StoredCredentials(
        new UserRecord(42, "fixture", "Fixture", "STUDENT"), hasher.hash("test-only"));
    var tokens = new TokenService(keys.getPrivate(), TokenTestResource.ISSUER,
        TokenTestResource.AUDIENCE, Clock.fixed(now, ZoneOffset.UTC));
    var response = new AuthService(databaseWith(credentials), hasher, tokens)
        .authenticate("fixture", "test-only", "Web_A-01");

    var signature = new org.jose4j.jws.JsonWebSignature();
    signature.setCompactSerialization(response.token());
    signature.setKey(keys.getPublic());
    assertEquals(org.jose4j.jws.AlgorithmIdentifiers.RSA_USING_SHA256,
        signature.getAlgorithmHeaderValue());
    assertTrue(signature.verifySignature());
    var claims = org.jose4j.jwt.JwtClaims.parse(signature.getPayload());
    assertEquals("fixture", claims.getSubject());
    assertEquals("Web_A-01", claims.getStringClaimValue("client"));
    assertEquals(3600, response.expiresIn());
    assertEquals(now.getEpochSecond(), claims.getIssuedAt().getValue());
    long remainingAtIssuance = claims.getExpirationTime().getValue() - now.getEpochSecond();
    assertTrue(Math.abs(response.expiresIn() - remainingAtIssuance) <= 1);
    assertEquals(3600L, remainingAtIssuance);
    }

    @Test
    void doesNotVerifyOrIssueForMissingUser() {
        var hasher = new PasswordHasher() {
            @Override
            public boolean verify(String password, String hash) {
                return fail("Missing users must not reach password verification");
            }
        };
        var service = new AuthService(databaseWith(null), hasher, tokensThatMustNotBeIssued());

        assertNull(service.authenticate("fixture", "exact password", "Web_A-01"));
    }

    @Test
    void doesNotIssueForIncorrectPassword() {
        var credentials = new LegacyDatabase.StoredCredentials(
                new UserRecord(42, "fixture", "Fixture", "CUSTOM"), "fixture-hash");
        var hasher = new PasswordHasher() {
            @Override
            public boolean verify(String password, String hash) {
                assertEquals("exact password", password);
                assertEquals("fixture-hash", hash);
                return false;
            }
        };
        var service = new AuthService(databaseWith(credentials), hasher, tokensThatMustNotBeIssued());

        assertNull(service.authenticate("fixture", "exact password", "Web_A-01"));
    }

    @Test
    void mapsPublicResponseOnlyAfterVerifyingCredentials() {
        var credentials = new LegacyDatabase.StoredCredentials(
                new UserRecord(42, "fixture", "<Fixture>", "CUSTOM"), "fixture-hash");
        boolean[] verified = {false};
        var hasher = new PasswordHasher() {
            @Override
            public boolean verify(String password, String hash) {
                assertEquals(" exact password ", password);
                assertEquals("fixture-hash", hash);
                verified[0] = true;
                return true;
            }
        };
        var tokens = new TokenService(null, "fixture-issuer", "fixture-audience", Clock.systemUTC()) {
            @Override
            public String create(String username, String role, String client) {
                assertTrue(verified[0]);
                assertEquals("fixture", username);
                assertEquals("CUSTOM", role);
                assertEquals("Web_A-01", client);
                return "fixture-token";
            }
        };
        var service = new AuthService(databaseWith(credentials), hasher, tokens);

        assertEquals(new LoginResponse("fixture-token", 3600,
                        new LoginResponse.User("fixture", "&lt;Fixture&gt;", "CUSTOM"),
                        "authenticated-with-legacy-flow"),
                service.authenticate("fixture", " exact password ", "Web_A-01"));
    }

    private static LegacyDatabase databaseWith(LegacyDatabase.StoredCredentials credentials) {
        return new LegacyDatabase() {
            @Override
            public StoredCredentials findByUsername(String username) {
                assertEquals("fixture", username);
                return credentials;
            }
        };
    }

    private static TokenService tokensThatMustNotBeIssued() {
        return new TokenService(null, "fixture-issuer", "fixture-audience", Clock.systemUTC()) {
            @Override
            public String create(String username, String role, String client) {
                return fail("Rejected credentials must not reach token emission");
            }
        };
    }

    static Stream<Arguments> displayNames() {
        return Stream.of(
                Arguments.of("Fixture <User> & \"Team\"", "Fixture &lt;User&gt; &amp; &quot;Team&quot;"),
                Arguments.of("O'Connor", "O'Connor"),
                Arguments.of("Jos\u00e9 Mu\u00f1oz \u20ac", "Jos&eacute; Mu&ntilde;oz &euro;"),
                Arguments.of("${literal} \uD83D\uDE00", "${literal} \uD83D\uDE00"));
    }

    @ParameterizedTest
    @MethodSource("displayNames")
    void preservesHtml4Escaping(String displayName, String expected) {
        var hasher = new PasswordHasher();
        String hash = hasher.hash("test-only");
        var database = new LegacyDatabase() {
            @Override
            public StoredCredentials findByUsername(String username) {
                return new StoredCredentials(new UserRecord(42, username, displayName, "CUSTOM"), hash);
            }
        };
        var service = new AuthService(database, hasher, TokenTestResource.service());

        assertEquals(new LoginResponse.User("fixture", expected, "CUSTOM"),
                service.authenticate("fixture", "test-only", "Web_A-01").user());
    }

    @Test
    void reportsInvalidPersistedHashRatherThanAuthenticating() {
        var database = new LegacyDatabase() {
            @Override
            public StoredCredentials findByUsername(String username) {
                return new StoredCredentials(new UserRecord(1, username, "Fixture", "STUDENT"), "bad-hash");
            }
        };
        var error = assertThrows(IllegalStateException.class,
                () -> new AuthService(database, new PasswordHasher(), TokenTestResource.service()).authenticate("test", "test", "Web_A-01"));
        assertEquals("Invalid stored password hash", error.getMessage());
        assertEquals("StoredCredentials[redacted]", database.findByUsername("test").toString());
    }
}
