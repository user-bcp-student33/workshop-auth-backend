package com.bcp.workshop.auth;

import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    @TempDir(factory = TokenTestResource.RepositoryTempDirectory.class)
    Path directory;

    @Test
    void emitsSignedCompactToken() throws Exception {
        KeyPair keys = TokenTestResource.keyPair();
        TokenService service = service(keys);
        String token = service.create("fixture", "STUDENT", "Web_A-01");
        assertEquals(3, token.split("\\.").length);
        var signature = new JsonWebSignature();
        signature.setCompactSerialization(token);
        assertEquals(AlgorithmIdentifiers.RSA_USING_SHA256, signature.getAlgorithmHeaderValue());
        var claims = verifier(keys, NOW).processToClaims(token);
        assertEquals("fixture", claims.getSubject());
        assertEquals("STUDENT", claims.getStringClaimValue("role"));
        assertEquals("Web_A-01", claims.getStringClaimValue("client"));
        assertEquals(TokenTestResource.ISSUER, claims.getIssuer());
        assertEquals(List.of(TokenTestResource.AUDIENCE), claims.getAudience());
        assertEquals(NOW.getEpochSecond() + TokenService.TTL_SECONDS,
                claims.getExpirationTime().getValue());
        assertEquals(NOW.getEpochSecond(), claims.getIssuedAt().getValue());
        assertEquals(3600L, claims.getExpirationTime().getValue() - claims.getIssuedAt().getValue());
        assertFalse(claims.getClaimsMap().containsKey("secret"));
        assertFalse(claims.getClaimsMap().containsKey("password"));
    }

    @Test
    void rejectsModifiedPayloadWrongKeyAndExpirationBoundary() throws Exception {
        KeyPair keys = TokenTestResource.keyPair();
        String token = service(keys).create("fixture", "STUDENT", "Web_A-01");
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
        parts[1] = Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.replace("STUDENT", "TRAINER").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(InvalidJwtException.class,
                () -> verifier(keys, NOW).processToClaims(String.join(".", parts)));
        assertThrows(InvalidJwtException.class,
                () -> verifier(TokenTestResource.keyPair(), NOW).processToClaims(token));
        verifier(keys, NOW.plusSeconds(TokenService.TTL_SECONDS - 1)).processToClaims(token);
        assertThrows(InvalidJwtException.class,
                () -> verifier(keys, NOW.plusSeconds(TokenService.TTL_SECONDS)).processToClaims(token));
    }

    private TokenService service(KeyPair keys) {
        return new TokenService(keys.getPrivate(), TokenTestResource.ISSUER,
                TokenTestResource.AUDIENCE, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void loadsPemFilesAndRejectsBadMissingMismatchedAndWeakKeys() throws Exception {
        KeyPair keys = TokenTestResource.keyPair();
        Path privatePem = directory.resolve("private.pem");
        Path publicPem = directory.resolve("public.pem");
        Files.writeString(privatePem, TokenTestResource.pem("PRIVATE KEY", keys.getPrivate().getEncoded()));
        Files.writeString(publicPem, TokenTestResource.pem("PUBLIC KEY", keys.getPublic().getEncoded()));
        TokenService loaded = new TokenService(TokenTestResource.ISSUER, TokenTestResource.AUDIENCE,
                privatePem.toString(), publicPem.toString());
        assertEquals(3, loaded.create("fixture", "STUDENT", "Web_A-01").split("\\.").length);
        assertThrows(IllegalStateException.class, () -> new TokenService("issuer", "audience",
                directory.resolve("missing").toString(), publicPem.toString()));
        Files.writeString(publicPem, "not a PEM");
        assertThrows(IllegalStateException.class, () -> new TokenService("issuer", "audience",
                privatePem.toString(), publicPem.toString()));
        Files.writeString(publicPem, TokenTestResource.pem("PUBLIC KEY", TokenTestResource.keyPair().getPublic().getEncoded()));
        assertThrows(IllegalStateException.class, () -> new TokenService("issuer", "audience",
                privatePem.toString(), publicPem.toString()));
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        var weak = generator.generateKeyPair();
        Files.writeString(privatePem, TokenTestResource.pem("PRIVATE KEY", weak.getPrivate().getEncoded()));
        Files.writeString(publicPem, TokenTestResource.pem("PUBLIC KEY", weak.getPublic().getEncoded()));
        assertThrows(IllegalStateException.class, () -> new TokenService("issuer", "audience",
                privatePem.toString(), publicPem.toString()));
        Files.writeString(privatePem, TokenTestResource.pem("PRIVATE KEY", new byte[] {1}));
        assertThrows(IllegalStateException.class, () -> new TokenService("issuer", "audience",
                privatePem.toString(), publicPem.toString()));
    }

    private JwtConsumer verifier(KeyPair keys, Instant time) {
        return new JwtConsumerBuilder().setVerificationKey(keys.getPublic())
                .setExpectedIssuer(TokenTestResource.ISSUER).setExpectedAudience(TokenTestResource.AUDIENCE)
                .setRequireExpirationTime().setRequireSubject().setAllowedClockSkewInSeconds(0)
                .setEvaluationTime(NumericDate.fromSeconds(time.getEpochSecond()))
                .setJwsAlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, AlgorithmIdentifiers.RSA_USING_SHA256)
                .build();
    }
}
