package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AuthService {
    private static final Logger LOG = Logger.getLogger(AuthService.class);
    private static final String DEBUG_FLOW = "authenticated-with-legacy-flow";
    private final LegacyDatabase database;
    private final PasswordHasher hasher;
    private final TokenService tokens;

    @Inject
    public AuthService(LegacyDatabase database, PasswordHasher hasher, TokenService tokens) {
        this.database = database;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    public LoginResponse authenticate(String username, String password) {
        var credentials = database.findByUsername(username);
        if (credentials == null) {
            return null;
        }
        if (!verifyPassword(password, credentials.passwordHash())) {
            return null;
        }
        return responseFor(credentials.user());
    }

    private boolean verifyPassword(String password, String hash) {
        try {
            return hasher.verify(password, hash);
        } catch (IllegalArgumentException error) {
            LOG.error("Authentication failed due to an invalid stored password hash");
            throw new IllegalStateException("Invalid stored password hash");
        }
    }

    private LoginResponse responseFor(UserRecord user) {
        var publicUser = new LoginResponse.User(user.username(),
                StringEscapeUtils.escapeHtml4(user.displayName()), user.role());
        return new LoginResponse(tokens.create(user.username(), user.role()),
                TokenService.TTL_SECONDS, publicUser, DEBUG_FLOW);
    }
}
