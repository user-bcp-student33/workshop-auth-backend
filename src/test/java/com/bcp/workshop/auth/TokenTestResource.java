package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;

public class TokenTestResource implements QuarkusTestResourceLifecycleManager {
    static final String ISSUER = "https://issuer.test";
    static final String AUDIENCE = "auth-tests";
    private Path directory;

    @Override
    public Map<String, String> start() {
        try {
            KeyPair keys = keyPair();
            directory = Files.createTempDirectory(Path.of("target"), "token-fixture-");
            Path privatePath = directory.resolve("private.pem").toAbsolutePath();
            Path publicPath = directory.resolve("public.pem").toAbsolutePath();
            Files.writeString(privatePath, pem("PRIVATE KEY", keys.getPrivate().getEncoded()));
            Files.writeString(publicPath, pem("PUBLIC KEY", keys.getPublic().getEncoded()));
            return Map.of("auth.token.issuer", ISSUER, "auth.token.audience", AUDIENCE,
                    "auth.token.private-key", privatePath.toString(), "auth.token.public-key", publicPath.toString(),
                    "auth.cors.origins", "https://allowed.test");
        } catch (Exception error) {
            throw new IllegalStateException("Cannot create isolated token fixture", error);
        }
    }

    @Override
    public void stop() {
        if (directory == null) {
            return;
        }
        try {
            Files.deleteIfExists(directory.resolve("private.pem"));
            Files.deleteIfExists(directory.resolve("public.pem"));
            Files.delete(directory);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot remove token fixture", error);
        }
    }

    static KeyPair keyPair() throws java.security.GeneralSecurityException {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    static TokenService service() {
        try {
            return new TokenService(keyPair().getPrivate(), ISSUER, AUDIENCE, Clock.systemUTC());
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("Cannot generate unit test key", error);
        }
    }

    static String pem(String label, byte[] bytes) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(bytes)
                + "\n-----END " + label + "-----\n";
    }

    public static class RepositoryTempDirectory implements org.junit.jupiter.api.io.TempDirFactory {
        @Override
        public Path createTempDirectory(org.junit.jupiter.api.extension.AnnotatedElementContext element,
                                        org.junit.jupiter.api.extension.ExtensionContext context)
                throws java.io.IOException {
            return Files.createTempDirectory(Path.of("target"), "auth-unit-");
        }
    }
}
