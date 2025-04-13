package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.logging.Logger;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
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
import pt.unl.fct.di.apdc.firstwebapp.util.ChangeRoleData;

@Path("/changerole")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
@Consumes(MediaType.APPLICATION_JSON)
public class ChangeRoleResource {

    // Logger for resource events.
    private static final Logger LOG = Logger.getLogger(ChangeRoleResource.class.getName());

    // Get the Datastore service instance.
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    // KeyFactory for "User" entities.
    private static final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");

    // KeyFactory for "AuthToken" entities.
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");

    // Gson instance to convert objects to JSON.
    private final Gson g = new Gson();

    /**
     * Endpoint for changing a user's role.
     * <p>
     * The request must include an Authorization header with a Bearer token and a JSON body
     * containing:
     * - targetUsername: the username of the target user.
     * - newRole: the new role to assign.
     * <p>
     * ADMIN users can change any role.
     * BACKOFFICE users can only swap roles between ENDUSER and PARTNER.
     */
    @POST
    public Response changeRole(@Context HttpHeaders headers, ChangeRoleData data) {
        // Extract the token from the "Authorization" header.
        String authHeader = headers.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Missing or invalid authorization header\"}")
                    .build();
        }
        String tokenStr = authHeader.substring("Bearer ".length()).trim();
        if (tokenStr.isBlank()) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Empty token\"}")
                    .build();
        }

        // Look up the token entity using the token string as key.
        Key tokenKey = tokenKeyFactory.newKey(tokenStr);
        Entity tokenEntity = datastore.get(tokenKey);
        if (tokenEntity == null) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Invalid token\"}")
                    .build();
        }

        // Check if the token is still valid by comparing its expiration timestamp with the current time.
        Timestamp validToTimestamp = tokenEntity.getTimestamp("valid_to");
        long expirationData = validToTimestamp.toDate().getTime();
        long currentTime = System.currentTimeMillis();
        if (currentTime > expirationData) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Token expired\"}")
                    .build();
        }

        // Retrieve the authenticated user's username and role from the token.
        String authUsername = tokenEntity.getString("username");
        String authUserRole = tokenEntity.getString("role");

        // Retrieve the target user entity from the datastore using the supplied targetUsername.
        Key targetUserKey = userKeyFactory.newKey(data.targetUsername);
        Entity targetUser = datastore.get(targetUserKey);
        if (targetUser == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("{\"error\": \"Target user not found\"}")
                    .build();
        }

        // Get the current role of the target user and convert the new role from the input to uppercase.
        String currentTargetRole = targetUser.getString("role");
        String newRole = data.newRole.toUpperCase();

        // Check permissions based on the authenticated user's role.
        boolean allowed = false;
        if ("ADMIN".equals(authUserRole)) {
            // ADMIN can change any role.
            allowed = true;
        } else if ("BACKOFFICE".equals(authUserRole)) {
            // BACKOFFICE can only toggle between ENDUSER and PARTNER.
            if ((currentTargetRole.equals("ENDUSER") && newRole.equals("PARTNER")) ||
                    (currentTargetRole.equals("PARTNER") && newRole.equals("ENDUSER"))) {
                allowed = true;
            }
        } // ENDUSER and other roles are not permitted to change roles.

        if (!allowed) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Permission denied for role change\"}")
                    .build();
        }

        // Update the target user's role.
        Entity updatedUser = Entity.newBuilder(targetUser)
                .set("role", newRole)
                .build();
        datastore.put(updatedUser);
        LOG.info("User role changed: " + data.targetUsername + " from " + currentTargetRole + " to " + newRole + " by " + authUsername);

        // Return a JSON response with the target username and its new role.
        String jsonResponse = String.format("{\"username\": \"%s\", \"newRole\": \"%s\"}",
                updatedUser.getKey().getName(), updatedUser.getString("role"));
        return Response.ok(jsonResponse).build();
    }
}
