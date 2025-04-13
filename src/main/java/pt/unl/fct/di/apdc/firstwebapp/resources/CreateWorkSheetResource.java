package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Logger;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.NullValue;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.Transaction;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.TimestampValue;
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
import pt.unl.fct.di.apdc.firstwebapp.util.CreateWorkSheetData;

@Path("/worksheets")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
@Consumes(MediaType.APPLICATION_JSON)
public class CreateWorkSheetResource {

    // Logger for logging events in this resource.
    private static final Logger LOG = Logger.getLogger(CreateWorkSheetResource.class.getName());

    // Datastore instance obtained from the default options.
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    // KeyFactory for AuthToken entities.
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");

    // KeyFactory for WorkSheet entities.
    private static final KeyFactory workSheetKeyFactory = datastore.newKeyFactory().setKind("WorkSheet");

    private final Gson g = new Gson();

    /**
     * Endpoint to create or update a worksheet.
     * It uses the "reference" as the unique identifier.
     * Based on the awardingStatus and the user role,
     * it either creates a new worksheet or updates an existing one.
     */
    @POST
    @Path("/create")
    public Response createOrUpdateWorkSheet(@Context HttpHeaders headers, CreateWorkSheetData data) {
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

        // 2. Look up the token entity in Datastore using the token string as the key.
        Key tokenKey = tokenKeyFactory.newKey(tokenStr);
        Entity tokenEntity = datastore.get(tokenKey);
        if (tokenEntity == null) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Invalid token: not found in datastore\"}")
                    .build();
        }

        // 3. Check if the token is expired.
        Timestamp validToTs = tokenEntity.getTimestamp("valid_to");
        long now = System.currentTimeMillis();
        if (validToTs == null || now > validToTs.toDate().getTime()) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Token expired\"}")
                    .build();
        }

        // 4. Retrieve the authenticated username from the token.
        String authUsername = tokenEntity.getString("username");

        // 5. Validate that the mandatory fields are present.
        if (data.reference == null || data.reference.isBlank() ||
                data.description == null || data.description.isBlank() ||
                data.targetType == null   || data.targetType.isBlank() ||
                data.awardingStatus == null || data.awardingStatus.isBlank()) {
            return Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"Missing mandatory fields: reference, description, targetType, awardingStatus\"}")
                    .build();
        }

        // Convert awardingStatus to uppercase for consistency.
        String awardingStatus = data.awardingStatus.toUpperCase();

        // 6. Create or retrieve the WorkSheet entity.
        Key workSheetKey = workSheetKeyFactory.newKey(data.reference);
        Entity existingWorksheet = datastore.get(workSheetKey);

        // 7. Authorization: Only BACKOFFICE can create a new worksheet if it doesn't exist.
        if (existingWorksheet == null) {
            if (!"BACKOFFICE".equalsIgnoreCase(tokenEntity.getString("role"))) {
                return Response.status(Status.FORBIDDEN)
                        .entity("{\"error\": \"Only BACKOFFICE can create new worksheets\"}")
                        .build();
            }
        } else {
            // If the worksheet exists, additional rules might apply for updating.
            // For example, PARTNER can only update the work state and observations
            // if they are the assigned partner (this logic is handled below).
        }

        // 8. Start building the worksheet entity
        Transaction txn = datastore.newTransaction();
        try {
            Entity.Builder builder;
            if (existingWorksheet == null) {
                // Building a new worksheet with the mandatory fields.
                builder = Entity.newBuilder(workSheetKey)
                        .set("reference", data.reference)
                        .set("description", data.description)
                        .set("targetType", data.targetType)
                        .set("awardingStatus", awardingStatus);

                // If the worksheet is adjudicated, populate the adjudication fields.
                if ("ADJUDICADO".equals(awardingStatus)) {
                    builder.set("awardingDate", data.awardingDate == null
                            ? NullValue.of()
                            : TimestampValue.of(Timestamp.of(data.awardingDate)));
                    builder.set("startDate", data.startDate == null
                            ? NullValue.of()
                            : TimestampValue.of(Timestamp.of(data.startDate)));
                    builder.set("endDate", data.endDate == null
                            ? NullValue.of()
                            : TimestampValue.of(Timestamp.of(data.endDate)));
                    builder.set("partnerAccount", data.partnerAccount == null ? "" : data.partnerAccount);
                    builder.set("awardingEntity", data.awardingEntity == null ? "" : data.awardingEntity);
                    builder.set("awardingNif", data.awardingNif == null ? "" : data.awardingNif);
                    builder.set("workState", data.workState == null ? "NÃO INICIADO" : data.workState);
                    builder.set("observations", data.observations == null ? "" : data.observations);
                } else {
                    // If not adjudicated, set all adjudication-related fields to empty or null.
                    builder.set("awardingDate", NullValue.of())
                            .set("startDate", NullValue.of())
                            .set("endDate", NullValue.of())
                            .set("partnerAccount", "")
                            .set("awardingEntity", "")
                            .set("awardingNif", "")
                            .set("workState", "")
                            .set("observations", "");
                }
            } else {
                // For existing worksheets, start with the current entity.
                builder = Entity.newBuilder(existingWorksheet);
                // BACKOFFICE users can update all fields if desired.
                if ("BACKOFFICE".equalsIgnoreCase(tokenEntity.getString("role"))) {
                    builder.set("description", data.description)
                            .set("targetType", data.targetType)
                            .set("awardingStatus", awardingStatus);

                    if ("ADJUDICADO".equals(awardingStatus)) {
                        builder.set("awardingDate", data.awardingDate == null
                                ? NullValue.of()
                                : TimestampValue.of(Timestamp.of(data.awardingDate)));
                        builder.set("startDate", data.startDate == null
                                ? NullValue.of()
                                : TimestampValue.of(Timestamp.of(data.startDate)));
                        builder.set("endDate", data.endDate == null
                                ? NullValue.of()
                                : TimestampValue.of(Timestamp.of(data.endDate)));
                        builder.set("partnerAccount", data.partnerAccount == null ? "" : data.partnerAccount);
                        builder.set("awardingEntity", data.awardingEntity == null ? "" : data.awardingEntity);
                        builder.set("awardingNif", data.awardingNif == null ? "" : data.awardingNif);
                        String currentState = existingWorksheet.contains("workState")
                                ? existingWorksheet.getString("workState") : "";
                        builder.set("workState", data.workState == null ? currentState : data.workState);
                        String currentObs = existingWorksheet.contains("observations")
                                ? existingWorksheet.getString("observations") : "";
                        builder.set("observations", data.observations == null ? currentObs : data.observations);
                    } else {
                        // Reset adjudication fields when not adjudicated
                        builder.set("awardingDate", NullValue.of())
                                .set("startDate", NullValue.of())
                                .set("endDate", NullValue.of())
                                .set("partnerAccount", "")
                                .set("awardingEntity", "")
                                .set("awardingNif", "")
                                .set("workState", "")
                                .set("observations", "");
                    }
                } else if ("PARTNER".equalsIgnoreCase(tokenEntity.getString("role"))) {
                    // A partner is allowed to update only workState and observations if they are assigned.
                    String currentAwardStatus = existingWorksheet.getString("awardingStatus");
                    String currentPartner = existingWorksheet.getString("partnerAccount");
                    if (!"ADJUDICADO".equalsIgnoreCase(currentAwardStatus) ||
                            !currentPartner.equals(authUsername)) {
                        return Response.status(Status.FORBIDDEN)
                                .entity("{\"error\": \"You are not the assigned partner or the worksheet is not adjudicated\"}")
                                .build();
                    }
                    if (data.workState != null && !data.workState.isBlank()) {
                        builder.set("workState", data.workState);
                    }
                    if (data.observations != null) {
                        builder.set("observations", data.observations);
                    }
                } else {
                    // Other roles are not permitted to update existing worksheets.
                    return Response.status(Status.FORBIDDEN)
                            .entity("{\"error\": \"This role cannot modify existing worksheets\"}")
                            .build();
                }
            }

            // 9. Save the new or updated worksheet entity within the transaction.
            Entity finalWorksheet = builder.build();
            txn.put(finalWorksheet);
            txn.commit();

            // 10. Convert the entity into a response-friendly JSON representation.
            return Response.ok(g.toJson(entityToWorkSheetResponse(finalWorksheet))).build();

        } catch (Exception e) {
            txn.rollback();
            LOG.severe("Error creating/updating worksheet: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Exception occurred\"}")
                    .build();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }

    /**
     * Converts the worksheet entity to a response object.
     * Fields that are not defined are returned as null.
     */
    private Object entityToWorkSheetResponse(Entity e) {
        return new Object() {
            public String reference        = e.getKey().getName();
            public String description      = e.getString("description");
            public String targetType       = e.getString("targetType");
            public String awardingStatus   = e.getString("awardingStatus");
            public String partnerAccount   = e.contains("partnerAccount") ? e.getString("partnerAccount") : null;
            public String awardingEntity   = e.contains("awardingEntity") ? e.getString("awardingEntity") : null;
            public String awardingNif      = e.contains("awardingNif")    ? e.getString("awardingNif")    : null;
            public String workState        = e.contains("workState")      ? e.getString("workState")      : null;
            public String observations     = e.contains("observations")   ? e.getString("observations")   : null;
            public Date awardingDate       = e.contains("awardingDate") && !e.isNull("awardingDate")
                    ? e.getTimestamp("awardingDate").toDate() : null;
            public Date startDate          = e.contains("startDate") && !e.isNull("startDate")
                    ? e.getTimestamp("startDate").toDate() : null;
            public Date endDate            = e.contains("endDate") && !e.isNull("endDate")
                    ? e.getTimestamp("endDate").toDate() : null;
        };
    }
}
