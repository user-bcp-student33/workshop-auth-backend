package com.bcp.workshop.auth;

import io.smallrye.jwt.build.Jwt;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.GeneralSecurityException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;

@ApplicationScoped
@Startup
public class TokenService {
    public static final int TTL_SECONDS = 3600;
    private static final int MIN_RSA_KEY_BITS = 2048;
    private static final String KEY_VALIDATION_PROBE = "auth-key-validation";
    private static final Logger LOG = Logger.getLogger(TokenService.class);
    private final PrivateKey privateKey;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    @Inject
    public TokenService(@ConfigProperty(name = "auth.token.issuer") String issuer,
                        @ConfigProperty(name = "auth.token.audience") String audience,
                        @ConfigProperty(name = "auth.token.private-key") String privateKeyPath,
                        @ConfigProperty(name = "auth.token.public-key") String publicKeyPath) {
        this(loadPrivateKey(privateKeyPath), issuer, audience, Clock.systemUTC());
        validateKeyPair(loadPublicKey(publicKeyPath));
    }

    TokenService(PrivateKey privateKey, String issuer, String audience, Clock clock) {
        this.privateKey = privateKey;
        this.issuer = required(issuer);
        this.audience = required(audience);
        this.clock = clock;
    }

    public String create(String username, String role) {
        long issuedAt = clock.instant().getEpochSecond();
        return Jwt.issuer(issuer).subject(username).audience(audience).claim("role", role)
                .issuedAt(issuedAt).expiresAt(issuedAt + TTL_SECONDS).sign(privateKey);
    }

    private static PrivateKey loadPrivateKey(String location) {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(readPem(location, "PRIVATE KEY")));
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException error) {
            throw invalidKey();
        }
    }

    private static PublicKey loadPublicKey(String location) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(readPem(location, "PUBLIC KEY")));
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException error) {
            throw invalidKey();
        }
    }

    private static byte[] readPem(String location, String label) throws java.io.IOException {
        String pem = Files.readString(Path.of(location));
        String header = "-----BEGIN " + label + "-----";
        String footer = "-----END " + label + "-----";
        if (!pem.startsWith(header) || !pem.trim().endsWith(footer)) {
            throw invalidKey();
        }
        return Base64.getDecoder().decode(pem.replace(header, "").replace(footer, "").replaceAll("\\s", ""));
    }

    private void validateKeyPair(PublicKey publicKey) {
        try {
            if (((java.security.interfaces.RSAKey) publicKey).getModulus().bitLength() < MIN_RSA_KEY_BITS) {
                throw invalidKey();
            }
            var signature = java.security.Signature.getInstance("SHA256withRSA");
            byte[] probe = KEY_VALIDATION_PROBE.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            signature.initSign(privateKey);
            signature.update(probe);
            byte[] signed = signature.sign();
            signature.initVerify(publicKey);
            signature.update(probe);
            if (!signature.verify(signed)) {
                throw invalidKey();
            }
        } catch (GeneralSecurityException error) {
            throw invalidKey();
        }
    }

    private static IllegalStateException invalidKey() {
        LOG.error("Token key configuration is invalid");
        return new IllegalStateException("Token key configuration is invalid");
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            LOG.error("Token issuer and audience must be configured");
            throw new IllegalStateException("Token issuer and audience must be configured");
        }
        return value;
    }
}
