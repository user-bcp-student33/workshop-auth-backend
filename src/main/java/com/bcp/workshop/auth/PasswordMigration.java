package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@ApplicationScoped
public class PasswordMigration {
    private static final Logger LOG = Logger.getLogger(PasswordMigration.class);
    private static final int HASH_SCHEMA_VERSION = 1;
    private static final String CREATE_USERS = "CREATE TABLE IF NOT EXISTS legacy_users "
            + "(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, "
            + "display_name TEXT NOT NULL, role TEXT NOT NULL)";
    private final PasswordHasher hasher;

    @Inject
    public PasswordMigration(PasswordHasher hasher) {
        this.hasher = hasher;
    }

    public void initialize(String databaseUrl) {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(databaseUrl)) {
                migrate(connection);
            }
        } catch (SQLException | java.io.IOException | IllegalArgumentException error) {
            // Do not put database values or credentials in startup logs or HTTP errors.
            LOG.error("Password database initialization failed; startup aborted");
            throw new IllegalStateException("Password database initialization failed");
        }
    }

    private void migrate(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA secure_delete=ON");
        }
        connection.setAutoCommit(false);
        try {
            int version = schemaVersion(connection);
            if (version != 0 && version != HASH_SCHEMA_VERSION) {
                throw new SQLException("Unsupported password schema");
            }
            if (version == HASH_SCHEMA_VERSION) {
                validateHashes(connection);
                connection.commit();
                return;
            }
            try (var statement = connection.createStatement()) {
                statement.executeUpdate(CREATE_USERS);
            }
            convertLegacyPasswords(connection);
            seedUser(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
            seedUser(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA user_version=" + HASH_SCHEMA_VERSION);
            }
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        }
    }

    private int schemaVersion(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("PRAGMA user_version")) {
            return rows.getInt(1);
        }
    }

    private void convertLegacyPasswords(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id,password FROM legacy_users");
             var update = connection.prepareStatement("UPDATE legacy_users SET password=? WHERE id=?")) {
            while (rows.next()) {
                update.setString(1, hasher.hash(rows.getString("password")));
                update.setLong(2, rows.getLong("id"));
                update.executeUpdate();
            }
        }
    }

    private void validateHashes(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT password FROM legacy_users")) {
            while (rows.next()) {
                hasher.validate(rows.getString(1));
            }
        }
    }

    private void seedUser(Connection connection, String username, String password,
                          String displayName, String role) throws SQLException {
        try (var insert = connection.prepareStatement(
                "INSERT OR IGNORE INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            insert.setString(1, username);
            insert.setString(2, hasher.hash(password));
            insert.setString(3, displayName);
            insert.setString(4, role);
            insert.executeUpdate();
        }
    }
}
