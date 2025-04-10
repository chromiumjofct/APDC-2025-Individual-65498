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
import pt.unl.fct.di.apdc.firstwebapp.util.ChangeAccountAttributesData;

@Path("/changeaccountattributes")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
@Consumes(MediaType.APPLICATION_JSON)
public class ChangeAccountAttributesResource {

    private static final Logger LOG = Logger.getLogger(ChangeAccountAttributesResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    // KeyFactory para entidades do tipo "User"
    private static final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");
    // KeyFactory para entidades de token (tipo "AuthToken")
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");

    private final Gson g = new Gson();

    @POST
    public Response changeAccountAttributes(@Context HttpHeaders headers, ChangeAccountAttributesData data) {
        // Extrai o token do header Authorization
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

        // Procura a entidade do token na datastore.
        // Nesta implementação, supomos que a key do token é o próprio token
        Key tokenKey = tokenKeyFactory.newKey(tokenStr);
        Entity tokenEntity = datastore.get(tokenKey);
        if (tokenEntity == null) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Invalid token\"}")
                    .build();
        }

        // Verifica se o token está expirado (assumindo que o campo "valid_to" é um Timestamp)
        Timestamp validTo = tokenEntity.getTimestamp("valid_to");
        if (validTo == null || System.currentTimeMillis() > validTo.toDate().getTime()) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Token expired\"}")
                    .build();
        }

        // Recupera o username e o role do token
        String authUsername = tokenEntity.getString("username");
        String authUserRole = tokenEntity.getString("role").toUpperCase();

        // Validação: se o usuário autenticado for ENDUSER, só pode modificar a própria conta.
        if ("ENDUSER".equals(authUserRole) && !authUsername.equals(data.getTargetUsername())) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"ENDUSER can only modify their own account\"}")
                    .build();
        }

        // Se for BACKOFFICE: somente pode modificar contas cujo role seja ENDUSER ou PARTNER.
        Key targetUserKey = userKeyFactory.newKey(data.getTargetUsername());
        Entity targetUser = datastore.get(targetUserKey);
        if (targetUser == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("{\"error\": \"Target user not found\"}")
                    .build();
        }
        String targetUserRole = targetUser.contains("role") ? targetUser.getString("role").toUpperCase() : "ENDUSER";
        if ("BACKOFFICE".equals(authUserRole)) {
            if (!("ENDUSER".equals(targetUserRole) || "PARTNER".equals(targetUserRole))) {
                return Response.status(Status.FORBIDDEN)
                        .entity("{\"error\": \"BACKOFFICE can only modify accounts with role ENDUSER or PARTNER\"}")
                        .build();
            }
        }

        // Inicie a construção da entidade atualizada, partindo da entidade já existente.
        Entity.Builder builder = Entity.newBuilder(targetUser);

        // Atualizações permitidas para todos os roles:
        if (data.getNewPhone() != null && !data.getNewPhone().isBlank()) {
            builder.set("user_phone", data.getNewPhone());
        }
        if (data.getNewAccountProfile() != null && !data.getNewAccountProfile().isBlank()) {
            builder.set("account_profile", data.getNewAccountProfile());
        }
        if (data.getNewCcNumber() != null && !data.getNewCcNumber().isBlank()) {
            builder.set("cc_number", data.getNewCcNumber());
        }
        if (data.getNewNif() != null && !data.getNewNif().isBlank()) {
            builder.set("nif", data.getNewNif());
        }
        if (data.getNewJobEntity() != null && !data.getNewJobEntity().isBlank()) {
            builder.set("job_entity", data.getNewJobEntity());
        }
        if (data.getNewJob() != null && !data.getNewJob().isBlank()) {
            builder.set("job", data.getNewJob());
        }
        if (data.getNewAdress() != null && !data.getNewAdress().isBlank()) {
            builder.set("adress", data.getNewAdress());
        }
        if (data.getNewJobEntityNif() != null && !data.getNewJobEntityNif().isBlank()) {
            builder.set("job_entity_nif", data.getNewJobEntityNif());
        }

        // Atualizações controladas (atributos não permitem alteração por ENDUSER ou BACKOFFICE):
        // Apenas ADMIN pode modificar: user_name, user_email, role, account_status
        if ("ADMIN".equals(authUserRole)) {
            if (data.getNewUserName() != null && !data.getNewUserName().isBlank()) {
                builder.set("user_name", data.getNewUserName());
            }
            if (data.getNewEmail() != null && !data.getNewEmail().isBlank()) {
                builder.set("user_email", data.getNewEmail());
            }
            if (data.getNewRole() != null && !data.getNewRole().isBlank()) {
                builder.set("role", data.getNewRole().toUpperCase());
            }
            if (data.getNewAccountStatus() != null && !data.getNewAccountStatus().isBlank()) {
                builder.set("account_status", data.getNewAccountStatus().toUpperCase());
            }
        }

        Entity updatedUser = builder.build();
        datastore.put(updatedUser);
        LOG.info("Account attributes updated for user " + data.getTargetUsername() + " by " + authUsername);

        // Retorna os dados atualizados - aqui retornamos o objeto completo atualizado em JSON
        return Response.ok(g.toJson(updatedUser)).build();
    }
}
