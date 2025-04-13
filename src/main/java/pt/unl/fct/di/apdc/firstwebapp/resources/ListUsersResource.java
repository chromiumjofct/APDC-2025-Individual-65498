package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.StructuredQuery.OrderBy;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.cloud.datastore.StructuredQuery;
import com.google.cloud.datastore.StructuredQuery.Filter;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;
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
import pt.unl.fct.di.apdc.firstwebapp.util.ListUsersData;

@Path("/listusers")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
@Consumes(MediaType.APPLICATION_JSON)
public class ListUsersResource {

    private static final Logger LOG = Logger.getLogger(ListUsersResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    // KeyFactory for AuthToken entities.
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");

    // KeyFactory for User entities.
    private static final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");

    private final Gson g = new Gson();

    /**
     * Endpoint to list user accounts.
     * Authorization is based on a Bearer token provided in the Authorization header.
     * The list returned depends on the role of the authenticated user.
     */
    @POST
    public Response listUsers(@Context HttpHeaders headers, ListUsersData inputData) {
        // Get the token from the Authorization header.
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

        // Retrieve the AuthToken entity using the token string as the key.
        Key tokenKey = tokenKeyFactory.newKey(tokenStr);
        Entity tokenEntity = datastore.get(tokenKey);
        if (tokenEntity == null) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Invalid token\"}")
                    .build();
        }

        // Check whether the token is still valid by comparing the 'valid_to' timestamp with the current time.
        Timestamp validTo = tokenEntity.contains("valid_to") ? tokenEntity.getTimestamp("valid_to") : null;
        if (validTo == null || System.currentTimeMillis() > validTo.toDate().getTime()) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Token expired\"}")
                    .build();
        }

        // Get the authenticated user's username and role.
        String authUsername = tokenEntity.getString("username");
        String authUserRole = tokenEntity.getString("role").toUpperCase();

        // Build query filters based on the role of the requester.
        List<StructuredQuery.Filter> filters = new ArrayList<>();

        if ("ENDUSER".equals(authUserRole)) {
            // End users see only accounts with role ENDUSER, public profile, and active account.
            filters.add(PropertyFilter.eq("role", "ENDUSER"));
            filters.add(PropertyFilter.eq("account_profile", "público"));
            filters.add(PropertyFilter.eq("account_status", "ATIVADA"));
        } else if ("BACKOFFICE".equals(authUserRole)) {
            // Backoffice users list only accounts with role ENDUSER, regardless of profile or status.
            filters.add(PropertyFilter.eq("role", "ENDUSER"));
        } else if ("ADMIN".equals(authUserRole)) {
            // Admin sees all accounts; no filter is needed.
        } else {
            // Any other role does not have permission.
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Not enough privileges to list users\"}")
                    .build();
        }

        // Build the query. If filters are specified, combine them with an AND operation.
        StructuredQuery<Entity> query;
        if (!filters.isEmpty()) {
            StructuredQuery.Filter[] filterArray = filters.toArray(new StructuredQuery.Filter[0]);
            StructuredQuery.Filter combinedFilter = CompositeFilter.and(filterArray[0], filterArray);
            query = Query.newEntityQueryBuilder()
                    .setKind("User")
                    .setFilter(combinedFilter)
                    .build();
        } else {
            query = Query.newEntityQueryBuilder()
                    .setKind("User")
                    .build();
        }

        QueryResults<Entity> results = datastore.run(query);

        // Process the query results and construct the response.
        List<Map<String, String>> usersList = new ArrayList<>();
        while (results.hasNext()) {
            Entity userEntity = results.next();
            // For backoffice requests, skip users whose role is not ENDUSER.
            if ("BACKOFFICE".equals(authUserRole)) {
                String userRole = userEntity.contains("role") ? userEntity.getString("role").toUpperCase() : "ENDUSER";
                if (!"ENDUSER".equals(userRole)) {
                    continue;
                }
            }
            Map<String, String> userMap = new HashMap<>();

            // Include the username, which is the entity key.
            String username = userEntity.getKey().getName();
            userMap.put("username", username != null ? username : "NOT DEFINED");

            if ("ENDUSER".equals(authUserRole)) {
                // ENDUSER role: Only show email and name.
                String email = userEntity.contains("user_email") ? userEntity.getString("user_email") : "NOT DEFINED";
                String name = userEntity.contains("user_name") ? userEntity.getString("user_name") : "NOT DEFINED";
                userMap.put("email", email);
                userMap.put("name", name);
            } else {
                // BACKOFFICE and ADMIN: Include more details.
                String email = userEntity.contains("user_email") ? userEntity.getString("user_email") : "NOT DEFINED";
                String name = userEntity.contains("user_name") ? userEntity.getString("user_name") : "NOT DEFINED";
                String profile = userEntity.contains("account_profile") ? userEntity.getString("account_profile") : "NOT DEFINED";
                String phone = userEntity.contains("user_phone") ? userEntity.getString("user_phone") : "NOT DEFINED";
                String accountStatus = userEntity.contains("account_status") ? userEntity.getString("account_status") : "NOT DEFINED";
                String role = userEntity.contains("role") ? userEntity.getString("role") : "NOT DEFINED";

                userMap.put("email", email);
                userMap.put("name", name);
                userMap.put("account_profile", profile);
                userMap.put("phone", phone);
                userMap.put("account_status", accountStatus);
                userMap.put("role", role);
            }

            usersList.add(userMap);
        }
        return Response.ok(g.toJson(usersList)).build();
    }
}
