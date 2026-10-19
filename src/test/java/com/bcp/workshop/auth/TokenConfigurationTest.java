package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenConfigurationTest {
    @TempDir(factory = TokenTestResource.RepositoryTempDirectory.class)
    Path directory;

    @Test
    void rejectsEmptyIssuerAndAudience() throws Exception {
        var keys = TokenTestResource.keyPair();
        Path privatePem = directory.resolve("private.pem");
        Path publicPem = directory.resolve("public.pem");
        Files.writeString(privatePem, TokenTestResource.pem("PRIVATE KEY", keys.getPrivate().getEncoded()));
        Files.writeString(publicPem, TokenTestResource.pem("PUBLIC KEY", keys.getPublic().getEncoded()));
        assertThrows(IllegalStateException.class,
                () -> new TokenService(" ", "tests", privatePem.toString(), publicPem.toString()));
        assertThrows(IllegalStateException.class,
                () -> new TokenService("https://issuer.test", " ", privatePem.toString(), publicPem.toString()));
    }
}
