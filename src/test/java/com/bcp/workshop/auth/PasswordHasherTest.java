package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashesWithIndependentSaltsAndPreservesExactPassword() {
        long start = System.nanoTime();
        String first = hasher.hash(" spaced ' test ");
        System.out.printf("PBKDF2 600000 hash duration: %.2f ms%n", (System.nanoTime() - start) / 1_000_000.0);
        String second = hasher.hash(" spaced ' test ");
        assertNotEquals(first, second);
        assertTrue(hasher.verify(" spaced ' test ", first));
        assertFalse(hasher.verify("spaced ' test", first));
        assertEquals(16, Base64.getDecoder().decode(first.split("\\$")[2]).length);
        assertEquals(32, Base64.getDecoder().decode(first.split("\\$")[3]).length);
        hasher.validate(first);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"plaintext", "pbkdf2-sha256$1$x$x", "pbkdf2-sha256$600000$?$?",
            "pbkdf2-sha256$600000$YQ==$YQ=="})
    void rejectsInvalidHashesWithoutExposingValue(String hash) {
        var error = assertThrows(IllegalArgumentException.class, () -> hasher.verify("test", hash));
        assertEquals("Invalid stored password hash", error.getMessage());
    }

    @Test
    void rejectsNullPasswordExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
    }
}
