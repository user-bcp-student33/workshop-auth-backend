package com.bcp.workshop.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyDatabaseTest {
    private static final String INSERT_USER =
            "INSERT OR REPLACE INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)";

    private Path databaseFile;
    private String databaseUrl;
    private LegacyDatabase database;

    @BeforeEach
    void initializeDatabase() throws Exception {
        Class.forName("org.sqlite.JDBC");
        databaseFile = Files.createTempFile(Path.of("target"), "h01-unit-", ".db");
        databaseUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        database = new LegacyDatabase(databaseUrl);
        database.initialize();
    }

    @AfterEach
    void removeDatabase() throws Exception {
        if (databaseFile != null) {
            Files.deleteIfExists(databaseFile);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "student, Workshop123!, Workshop Student, STUDENT",
            "trainer, Trainer123!, Workshop Trainer, TRAINER"
    })
    void characterizesSeededUsers(String username, String password, String displayName, String role) {
        UserRecord user = findByCredentials(username, password);

        assertNotNull(user);
        assertTrue(user.id() > 0);
        assertEquals(username, user.username());
        assertEquals(displayName, user.displayName());
        assertEquals(role, user.role());
    }

    @ParameterizedTest
    @CsvSource({
            "student, incorrect",
            "unknown-user, Workshop123!",
            "' student ', Workshop123!",
            "student, ' Workshop123! '"
    })
    void characterizesRejectedCredentials(String username, String password) {
        assertNull(findByCredentials(username, password));
    }

    @Test
    void characterizesSignificantSpaces() throws Exception {
        insertUser(" spaced-user ", " spaced-password ");

        assertNotNull(findByCredentials(" spaced-user ", " spaced-password "));
        assertNull(findByCredentials("spaced-user", " spaced-password "));
        assertNull(findByCredentials(" spaced-user ", "spaced-password"));
    }

    @Test
    void characterizesRepeatedInitialization() {
        UserRecord before = findByCredentials("student", "Workshop123!");

        database.initialize();

        assertEquals(before, findByCredentials("student", "Workshop123!"));
    }

    @Test
    void characterizesQueryFailureAsNoMatch() throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE legacy_users RENAME TO unavailable_users");
            try {
                assertNull(findByCredentials("student", "Workshop123!"));
            } finally {
                statement.executeUpdate("ALTER TABLE unavailable_users RENAME TO legacy_users");
            }
        }
    }

    @Test
    void characterizesInitializationFailurePreservesExistingUsers() throws Exception {
        UserRecord before = findByCredentials("student", "Workshop123!");
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TRIGGER reject_seed BEFORE INSERT ON legacy_users "
                    + "BEGIN SELECT RAISE(ABORT, 'isolated initialization failure'); END");
            try {
                database.initialize();
                assertEquals(before, findByCredentials("student", "Workshop123!"));
            } finally {
                statement.executeUpdate("DROP TRIGGER reject_seed");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"' OR '1'='1' --", "student' --"})
    void rejectsSqlSyntaxInUsername(String username) {
        assertNull(findByCredentials(username, "incorrect"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"' OR '1'='1' --", "' OR 1=1 --"})
    void rejectsSqlSyntaxInPassword(String password) {
        assertNull(findByCredentials("student", password));
    }

    @Test
    void matchesQuotedCredentialsLiterally() throws Exception {
        insertUser("O'Connor", "test'password");

        assertNotNull(findByCredentials("O'Connor", "test'password"));
        assertNull(findByCredentials("O'Connor", "incorrect"));
    }

    private void insertUser(String username, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(INSERT_USER)) {
            statement.setString(1, username);
            statement.setString(2, new PasswordHasher().hash(password));
            statement.setString(3, "Fixture <User>");
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }
    }

    private UserRecord findByCredentials(String username, String password) {
        LoginResponse response = new AuthService(database, new PasswordHasher(), TokenTestResource.service())
                .authenticate(username, password, "Web_A-01");
        return response == null ? null : database.findByUsername(username).user();
    }
}
