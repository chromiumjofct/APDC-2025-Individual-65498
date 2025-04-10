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

    private static final Logger LOG = Logger.getLogger(ChangeRoleResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    // KeyFactory para entidades de tipo "User"
    private static final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");
    // KeyFactory para tokens – neste exemplo, supõe-se que a entidade do token é do tipo "AuthToken
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");

    private final Gson g = new Gson();

    /**
     * Endpoint para mudança de role.
     * Exemplo de uso:
     * - O header "Authorization" deve conter: "Bearer <token>"
     * - O corpo JSON deve conter targetUsername e newRole
     */
    @POST
    public Response changeRole(@Context HttpHeaders headers, ChangeRoleData data) {
        // Obtém o token do header Authorization
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

        // Procura o token na datastore;
        // Supõe-se que durante o login foi criado um token armazenado com
        // key igual ao token string.
        Key tokenKey = tokenKeyFactory.newKey(tokenStr);
        Entity tokenEntity = datastore.get(tokenKey);
        if (tokenEntity == null) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Invalid token\"}")
                    .build();
        }

        // Verifica se o token ainda é válido (ex.: comparar o campo "expirationData" com o tempo atual)
        Timestamp validToTimestamp = tokenEntity.getTimestamp("valid_to");
        long expirationData = validToTimestamp.toDate().getTime();
        long currentTime = System.currentTimeMillis();
        if (currentTime > expirationData) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Token expired\"}")
                    .build();
        }

        // Recupera o nome de utilizador e o role do token
        String authUsername = tokenEntity.getString("username");
        String authUserRole = tokenEntity.getString("role");

        // Para efeitos desta operação, verificamos se o utilizador autenticado tem
        // permissão para mudar o role do utilizador alvo.
        Key targetUserKey = userKeyFactory.newKey(data.targetUsername);
        Entity targetUser = datastore.get(targetUserKey);
        if (targetUser == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("{\"error\": \"Target user not found\"}")
                    .build();
        }
        String currentTargetRole = targetUser.getString("role");
        String newRole = data.newRole.toUpperCase();

        // Verifica permissões:
        boolean allowed = false;
        if ("ADMIN".equals(authUserRole)) {
            // ADMIN pode mudar qualquer role para qualquer role.
            allowed = true;
        } else if ("BACKOFFICE".equals(authUserRole)) {
            // BACKOFFICE pode alterar apenas: ENDUSER <-> PARTNER
            if ((currentTargetRole.equals("ENDUSER") && newRole.equals("PARTNER")) ||
                    (currentTargetRole.equals("PARTNER") && newRole.equals("ENDUSER"))) {
                allowed = true;
            }
        } else {
            // Outros (por exemplo, ENDUSER) não têm permissão para mudar roles.
            allowed = false;
        }

        if (!allowed) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Permission denied for role change\"}")
                    .build();
        }

        // Atualiza o role do utilizador alvo
        Entity updatedUser = Entity.newBuilder(targetUser)
                .set("role", newRole)
                .build();
        datastore.put(updatedUser);
        LOG.info("User role changed: " + data.targetUsername + " from " + currentTargetRole + " to " + newRole + " by " + authUsername);

        // Retorna uma resposta com os dados atualizados do utilizador
        // (neste exemplo, retorna o username e o novo role)
        String jsonResponse = String.format("{\"username\": \"%s\", \"newRole\": \"%s\"}",
                updatedUser.getKey().getName(), updatedUser.getString("role"));
        return Response.ok(jsonResponse).build();
    }
}

