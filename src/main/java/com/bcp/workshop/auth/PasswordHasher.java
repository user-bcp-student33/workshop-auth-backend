package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@ApplicationScoped
public class PasswordHasher {
    static final int ITERATIONS = 600_000;
    static final int SALT_BYTES = 16;
    static final int KEY_BYTES = 32;
    private static final String FORMAT = "pbkdf2-sha256";
    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return FORMAT + "$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(derive(password, salt));
    }

    public boolean verify(String password, String storedHash) {
        byte[][] decoded = decode(storedHash);
        return MessageDigest.isEqual(decoded[1], derive(password, decoded[0]));
    }

    public void validate(String storedHash) {
        decode(storedHash);
    }

    private byte[][] decode(String storedHash) {
        if (storedHash == null) {
            throw invalidHash();
        }
        String[] fields = storedHash.split("\\$", -1);
        if (fields.length != 4 || !FORMAT.equals(fields[0])
                || !Integer.toString(ITERATIONS).equals(fields[1])) {
            throw invalidHash();
        }
        try {
            byte[] salt = Base64.getDecoder().decode(fields[2]);
            byte[] key = Base64.getDecoder().decode(fields[3]);
            if (salt.length != SALT_BYTES || key.length != KEY_BYTES) {
                throw invalidHash();
            }
            return new byte[][] {salt, key};
        } catch (IllegalArgumentException error) {
            throw invalidHash();
        }
    }

    private byte[] derive(String password, byte[] salt) {
        if (password == null) {
            throw new IllegalArgumentException("Password is required");
        }
        char[] characters = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(characters, salt, ITERATIONS, KEY_BYTES * Byte.SIZE);
        Arrays.fill(characters, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing is unavailable", error);
        } finally {
            spec.clearPassword();
        }
    }

    private IllegalArgumentException invalidHash() {
        return new IllegalArgumentException("Invalid stored password hash");
    }
}
