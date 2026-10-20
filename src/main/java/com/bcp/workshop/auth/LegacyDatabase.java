package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

@ApplicationScoped
public class LegacyDatabase {
    private static final Logger LOG = Logger.getLogger(LegacyDatabase.class);
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
    private static final String FIND_BY_USERNAME_SQL =
            "SELECT id,username,display_name,role,password FROM legacy_users WHERE username=?";
    private final String databaseUrl;
    private final PasswordMigration migration;

    public LegacyDatabase() {
        this(DB_URL, new PasswordMigration(new PasswordHasher()));
    }

    @Inject
    public LegacyDatabase(PasswordMigration migration) {
        this(DB_URL, migration);
    }

    LegacyDatabase(String databaseUrl) {
        this(databaseUrl, new PasswordMigration(new PasswordHasher()));
    }

    private LegacyDatabase(String databaseUrl, PasswordMigration migration) {
        this.databaseUrl = databaseUrl;
        this.migration = migration;
    }

    @PostConstruct
    void initialize() {
        migration.initialize(databaseUrl);
    }

    public StoredCredentials findByUsername(String username) {
        try (var connection = DriverManager.getConnection(databaseUrl);
             var statement = connection.prepareStatement(FIND_BY_USERNAME_SQL)) {
            statement.setString(1, username);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return mapCredentials(rows);
            }
        } catch (SQLException error) {
            LOG.error("Authentication database query failed");
            return null;
        }
    }

    private StoredCredentials mapCredentials(ResultSet rows) throws SQLException {
        UserRecord user = new UserRecord(rows.getLong("id"), rows.getString("username"),
                rows.getString("display_name"), rows.getString("role"));
        return new StoredCredentials(user, rows.getString("password"));
    }

    public record StoredCredentials(UserRecord user, String passwordHash) {
        @Override
        public String toString() {
            return "StoredCredentials[redacted]";
        }
    }
}
