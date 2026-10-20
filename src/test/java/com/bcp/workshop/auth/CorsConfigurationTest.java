package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsConfigurationTest {
    @Test
    void acceptsExplicitOrigins() {
        assertDoesNotThrow(() -> new CorsConfiguration("https://allowed.test,http://localhost:4200"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "*", "/.*/", "https://*.test", "https://allowed.test/",
            "https://user@allowed.test", "https://allowed.test?q=1", "https://allowed.test#x",
            "ftp://allowed.test", "https://allowed.test:65536", "https://allowed.test:0",
            "https://allowed.test,", "not an origin"})
    void rejectsInvalidOrPermissiveOrigins(String origins) {
        assertThrows(IllegalStateException.class, () -> new CorsConfiguration(origins));
    }
}
