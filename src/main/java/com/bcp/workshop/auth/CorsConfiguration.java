package com.bcp.workshop.auth;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URISyntaxException;

@Startup
@ApplicationScoped
public class CorsConfiguration {
    private static final Logger LOG = Logger.getLogger(CorsConfiguration.class);
    private static final int MAX_PORT = 65535;

    @Inject
    public CorsConfiguration(@ConfigProperty(name = "auth.cors.origins") String origins) {
        if (origins == null || origins.isBlank()) {
            throw invalidOrigins();
        }
        for (String value : origins.split(",", -1)) {
            validateOrigin(value.trim());
        }
    }

    private void validateOrigin(String value) {
        try {
            URI origin = new URI(value);
            if (!("https".equals(origin.getScheme()) || "http".equals(origin.getScheme()))
                    || origin.getHost() == null || origin.getUserInfo() != null
                    || !origin.getRawPath().isEmpty() || origin.getRawQuery() != null
                    || origin.getRawFragment() != null || value.contains("*")
                    || origin.getPort() > MAX_PORT || origin.getPort() == 0) {
                throw invalidOrigins();
            }
        } catch (URISyntaxException error) {
            throw invalidOrigins();
        }
    }

    private IllegalStateException invalidOrigins() {
        LOG.error("CORS requires an explicit list of HTTP(S) origins without wildcards");
        return new IllegalStateException("CORS origins are invalid");
    }
}
