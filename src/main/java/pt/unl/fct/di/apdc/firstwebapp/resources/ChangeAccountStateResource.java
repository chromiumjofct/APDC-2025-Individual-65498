package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.logging.Logger;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.StructuredQuery;
import com.google.cloud.datastore.StringValue;
import com.google.gson.Gson;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import pt.unl.fct.di.apdc.firstwebapp.util.ChangeAccountStateData;

@Path("/changeaccountstate")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
@Consumes(MediaType.APPLICATION_JSON)
public class ChangeAccountStateResource {

    // Logger used for logging events
    private static final Logger LOG = Logger.getLogger(ChangeAccountStateResource.class.getName());

    // Datastore service instance
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    // KeyFactory for the "User" entities
    private static final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");
    // KeyFactory for the token entities ("AuthToken")
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");

    private final Gson g = new Gson();

    /**
     * REST endpoint to change a user's account state.
     * Expected JSON payload contains:
     * - targetUsername: the username whose account state will be updated
     * - newState: the new account state (for example "ATIVADA" or "DESATIVADA")
     *
     * Permissions:
     * - ADMIN can change the state of any account.
     * - BACKOFFICE can change the state between "ATIVADA" and "DESATIVADA".
     */
    @POST
    public Response changeAccountState(@Context HttpHeaders headers, ChangeAccountStateData data) {
        // Extract the Bearer token from the Authorization header.
        String authHeader = headers.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\":\"Missing or invalid authorization header\"}")
                    .build();
        }
        String tokenStr = authHeader.substring("Bearer ".length()).trim();
        if (tokenStr.isBlank()) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\":\"Empty token\"}")
                    .build();
        }

        // Look up the token entity in the datastore.
        // In this implementation, the token string is used as the key name.
        Key tokenKey = tokenKeyFactory.newKey(tokenStr);
        Entity tokenEntity = datastore.get(tokenKey);
        if (tokenEntity == null) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\":\"Invalid token\"}")
                    .build();
        }

        // Check token expiration by verifying that the current time does not exceed the valid_to value.
        Timestamp validToTimestamp = tokenEntity.getTimestamp("valid_to");
        long expirationMillis = validToTimestamp.toDate().getTime();
        if (System.currentTimeMillis() > expirationMillis) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\":\"Token expired\"}")
                    .build();
        }

        // Retrieve the authenticated user's username and role from the token.
        String authUsername = tokenEntity.getString("username");
        String authUserRole = tokenEntity.getString("role");

        // Retrieve the target user to update using the targetUsername from the request data.
        Key targetUserKey = userKeyFactory.newKey(data.getTargetUsername());
        Entity targetUser = datastore.get(targetUserKey);
        if (targetUser == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("{\"error\":\"Target user not found\"}")
                    .build();
        }

        // Get the current account state and convert newState to uppercase.
        String currentState = targetUser.getString("account_status");
        String newState = data.getNewState().toUpperCase();

        // Validate input: if targetUsername is null or newState is invalid, return a bad request.
        if (data.getTargetUsername() == null || !data.isValidState()) {
            return Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"Invalid input. Check targetUsername and newState.\"}")
                    .build();
        }

        // Check permission based on role:
        // ADMIN is allowed to change any account state.
        // BACKOFFICE can only switch between ATIVADA and DESATIVADA.
        boolean allowed = false;
        if ("ADMIN".equals(authUserRole)) {
            allowed = true;
        } else if ("BACKOFFICE".equals(authUserRole)) {
            if ((currentState.equals("ATIVADA") && newState.equals("DESATIVADA")) ||
                    (currentState.equals("DESATIVADA") && newState.equals("ATIVADA"))) {
                allowed = true;
            }
        }
        if (!allowed) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\":\"Permission denied for account state change\"}")
                    .build();
        }

        // If the current state is the same as the new state, no update is needed.
        if (currentState.equals(newState)) {
            return Response.status(Status.OK)
                    .entity("{\"message\":\"Account state is already " + newState + "\"}")
                    .build();
        }

        // Update the account_status field of the target user.
        Entity updatedUser = Entity.newBuilder(targetUser)
                .set("account_status", newState)
                .build();
        datastore.put(updatedUser);
        LOG.info("Account state changed for user " + data.getTargetUsername() +
                " from " + currentState + " to " + newState +
                " by " + authUsername);

        // Return a JSON response with the updated username and new account state.
        String jsonResponse = String.format("{\"username\": \"%s\", \"newAccountState\": \"%s\"}",
                updatedUser.getKey().getName(), updatedUser.getString("account_status"));
        return Response.ok(jsonResponse).build();
    }
}
