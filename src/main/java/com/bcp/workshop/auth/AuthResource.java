package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOG = Logger.getLogger(AuthResource.class);
    private static final String INTERNAL_ERROR_MESSAGE = "Internal server error";
    private static final String INTERNAL_ERROR_CODE = "InternalServerError";
    @Inject
    AuthService service;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        try {
            String validationMessage = validate(request);
            if (validationMessage != null) {
                return messageResponse(Response.Status.BAD_REQUEST, validationMessage);
            }
            LoginResponse authenticated = service.authenticate(request.username, request.password);
            if (authenticated == null) {
                return messageResponse(Response.Status.UNAUTHORIZED, "Invalid user " + request.username + " or password");
            }
            return Response.ok(authenticated).build();
        } catch (Exception error) {
            return internalErrorResponse(error);
        }
    }

    private Response internalErrorResponse(Exception error) {
        // Copy frames only: exception messages, causes and suppressed errors can contain credentials.
        var diagnostic = new Exception(INTERNAL_ERROR_MESSAGE);
        diagnostic.setStackTrace(error.getStackTrace());
        LOG.error("Authentication failed (" + error.getClass().getName() + ")", diagnostic);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Map.of(
                "message", INTERNAL_ERROR_MESSAGE, "exception", INTERNAL_ERROR_CODE)).build();
    }

    private String validate(LoginRequest request) {
        if (request == null) {
            return "Request is null";
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return "Username is required";
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return "Password is required";
        }
        return null;
    }

    private Response messageResponse(Response.Status status, String message) {
        return Response.status(status).entity(Map.of("message", message)).build();
    }
}
