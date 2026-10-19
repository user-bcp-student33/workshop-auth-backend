package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    @Test
    void doesNotVerifyOrIssueForMissingUser() {
        var hasher = new PasswordHasher() {
            @Override
            public boolean verify(String password, String hash) {
                return fail("Missing users must not reach password verification");
            }
        };
        var service = new AuthService(databaseWith(null), hasher, tokensThatMustNotBeIssued());

        assertNull(service.authenticate("fixture", "exact password"));
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

        assertNull(service.authenticate("fixture", "exact password"));
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
            public String create(String username, String role) {
                assertTrue(verified[0]);
                assertEquals("fixture", username);
                assertEquals("CUSTOM", role);
                return "fixture-token";
            }
        };
        var service = new AuthService(databaseWith(credentials), hasher, tokens);

        assertEquals(new LoginResponse("fixture-token", 3600,
                        new LoginResponse.User("fixture", "&lt;Fixture&gt;", "CUSTOM"),
                        "authenticated-with-legacy-flow"),
                service.authenticate("fixture", " exact password "));
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
            public String create(String username, String role) {
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
                service.authenticate("fixture", "test-only").user());
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
                () -> new AuthService(database, new PasswordHasher(), TokenTestResource.service()).authenticate("test", "test"));
        assertEquals("Invalid stored password hash", error.getMessage());
        assertEquals("StoredCredentials[redacted]", database.findByUsername("test").toString());
    }
}
