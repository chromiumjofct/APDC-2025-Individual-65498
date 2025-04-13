package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.logging.Logger;

import org.apache.commons.codec.digest.DigestUtils;

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
import pt.unl.fct.di.apdc.firstwebapp.util.ChangePasswordData;

@Path("/changepassword")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
@Consumes(MediaType.APPLICATION_JSON)
public class ChangePasswordResource {

    // Logger for logging events
    private static final Logger LOG = Logger.getLogger(ChangePasswordResource.class.getName());

    // Obtain the Datastore service
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    // KeyFactory for User entities; User keys are based on the username
    private static final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");
    // KeyFactory for AuthToken entities; here the token string is used as the key name
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");

    private final Gson g = new Gson();

    @POST
    public Response changePassword(@Context HttpHeaders headers, ChangePasswordData data) {
        // Check that all required password fields are provided in the input.
        if (data.getOldPassword() == null || data.getOldPassword().isBlank() ||
                data.getNewPassword() == null || data.getNewPassword().isBlank() ||
                data.getConfirmPassword() == null || data.getConfirmPassword().isBlank()) {
            return Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"Missing required password fields\"}")
                    .build();
        }

        // Verify that the new password and confirmation match.
        if (!data.getNewPassword().equals(data.getConfirmPassword())) {
            return Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"New password and confirmation do not match\"}")
                    .build();
        }

        // Extract the token from the Authorization header.
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

        // Look up the token entity from the datastore by its key.
        Key tokenKey = tokenKeyFactory.newKey(tokenStr);
        Entity tokenEntity = datastore.get(tokenKey);
        if (tokenEntity == null) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Invalid token\"}")
                    .build();
        }

        // Check if the token is expired by comparing its "valid_to" timestamp with the current time.
        Timestamp validTo = tokenEntity.getTimestamp("valid_to");
        long now = System.currentTimeMillis();
        if (validTo == null || now > validTo.toDate().getTime()) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Token expired\"}")
                    .build();
        }

        // Get the username from the token so that the user can only change their own password.
        String authUsername = tokenEntity.getString("username");

        // Retrieve the User entity from the datastore using the authenticated username.
        Key userKey = userKeyFactory.newKey(authUsername);
        Entity userEntity = datastore.get(userKey);
        if (userEntity == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("{\"error\": \"User account not found\"}")
                    .build();
        }

        // Validate the current (old) password provided in the request.
        String storedHashedPassword = userEntity.getString("user_pwd");
        if (!storedHashedPassword.equals(DigestUtils.sha512Hex(data.getOldPassword()))) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Incorrect current password\"}")
                    .build();
        }

        // All validations passed: generate the new hashed password and update the user entity.
        String newHashedPassword = DigestUtils.sha512Hex(data.getNewPassword());
        Entity updatedUser = Entity.newBuilder(userEntity)
                .set("user_pwd", newHashedPassword)
                .build();
        datastore.put(updatedUser);

        LOG.info("Password changed successfully for user: " + authUsername);
        return Response.ok("{\"message\": \"Password changed successfully\"}").build();
    }
}
