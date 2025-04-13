package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.logging.Logger;

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

@Path("/sessionlogout")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
@Consumes(MediaType.APPLICATION_JSON)
public class SessionLogoutResource {

    private static final Logger LOG = Logger.getLogger(SessionLogoutResource.class.getName());

    // Instantiate the Datastore service.
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    // KeyFactory for AuthToken entities. In this implementation, the token string is used as the key.
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");

    private final Gson g = new Gson();

    /**
     * Endpoint for session logout.
     * The client must send the token in the "Authorization" header in the format:
     *     Authorization: Bearer <token>
     *
     * If the token is found in the datastore, it is removed.
     * After logout the token cannot be used again.
     */
    @POST
    public Response logout(@Context HttpHeaders headers) {
        // 1. Extract the token from the "Authorization" header.
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

        // 2. Retrieve the token entity from the datastore.
        Key tokenKey = tokenKeyFactory.newKey(tokenStr);
        Entity tokenEntity = datastore.get(tokenKey);
        if (tokenEntity == null) {
            // It is acceptable to consider logout successful if the token is not found.
            return Response.status(Status.OK)
                    .entity("{\"message\": \"Token not found or already revoked\"}")
                    .build();
        }

        // Delete the token entity from the datastore.
        datastore.delete(tokenKey);
        LOG.info("Token " + tokenStr + " revoked successfully.");
        return Response.ok("{\"message\": \"Logout successful\"}").build();
    }
}
