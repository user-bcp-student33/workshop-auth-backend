package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

class PasswordMigrationTest {
    @TempDir(factory = TokenTestResource.RepositoryTempDirectory.class)
    Path directory;

    @Test
    void storesOnlyVersionedHashes() throws Exception {
        Class.forName("org.sqlite.JDBC");
        String url = "jdbc:sqlite:" + directory.resolve("users.db");
        new LegacyDatabase(url).initialize();
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT password FROM legacy_users")) {
            int count = 0;
            while (rows.next()) {
                assertTrue(rows.getString(1).startsWith("pbkdf2-sha256$600000$"),
                        "Persisted credentials must be versioned hashes, never plaintext");
                count++;
            }
            assertEquals(2, count);
        }
    }

    @Test
    void migratesLegacyRowsAndDoesNotRehashOnSecondStartup() throws Exception {
        String url = legacyDatabase();
        var database = new LegacyDatabase(url);
        database.initialize();
        var credentials = database.findByUsername("legacy");
        assertEquals(7, credentials.user().id());
        assertEquals("CUSTOM", credentials.user().role());
        assertTrue(new PasswordHasher().verify(" legacy ' password ", credentials.passwordHash()));
        database.initialize();
        assertEquals(credentials, database.findByUsername("legacy"));
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("PRAGMA user_version")) {
            assertEquals(1, rows.getInt(1));
        }
    }

    @Test
    void rollsBackMigrationAndAbortsStartupOnUpdateFailure() throws Exception {
        String url = legacyDatabase();
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_update BEFORE UPDATE ON legacy_users "
                    + "BEGIN SELECT RAISE(ABORT,'fixture failure'); END");
        }
        assertThrows(IllegalStateException.class, () -> new LegacyDatabase(url).initialize());
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT password FROM legacy_users WHERE id=7")) {
                assertEquals(" legacy ' password ", rows.getString(1));
            }
            try (var rows = statement.executeQuery("PRAGMA user_version")) {
                assertEquals(0, rows.getInt(1));
            }
        }
    }

    @Test
    void rejectsUnsupportedSchemaAndCorruptedHash() throws Exception {
        String url = legacyDatabase();
        var database = new LegacyDatabase(url);
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version=2");
            assertThrows(IllegalStateException.class, database::initialize);
            statement.execute("PRAGMA user_version=1");
            assertThrows(IllegalStateException.class, database::initialize);
        }
    }

    private String legacyDatabase() throws Exception {
        Class.forName("org.sqlite.JDBC");
        String url = "jdbc:sqlite:" + directory.resolve("legacy.db");
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
            try (var insert = connection.prepareStatement("INSERT INTO legacy_users VALUES (7,?,?,?,?)")) {
                insert.setString(1, "legacy");
                insert.setString(2, " legacy ' password ");
                insert.setString(3, "Fixture");
                insert.setString(4, "CUSTOM");
                insert.executeUpdate();
            }
        }
        return url;
    }
}
