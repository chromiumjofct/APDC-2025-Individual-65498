package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.StructuredQuery;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
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
import pt.unl.fct.di.apdc.firstwebapp.util.RemoveUserAccountData;

@Path("/removeuser")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
@Consumes(MediaType.APPLICATION_JSON)
public class RemoveUserAccountResource {

    private static final Logger LOG = Logger.getLogger(RemoveUserAccountResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    // Tokens: as entidades de token são do Kind "AuthToken" e possuem um campo "username"
    // para associá-las ao usuário.
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");

    // Usuários: entidades do Kind "User", com key igual ao username (ou outro identificador único)
    private static final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");

    private final Gson g = new Gson();

    @POST
    public Response removeUser(@Context HttpHeaders headers, RemoveUserAccountData data) {
        // 1. Extrair o token do cabeçalho "Authorization"
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

        // 2. Buscar a entidade do token na datastore (supondo que o token é a keyName)
        Key tokenKey = tokenKeyFactory.newKey(tokenStr);
        Entity tokenEntity = datastore.get(tokenKey);
        if (tokenEntity == null) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Invalid token: not found in datastore\"}")
                    .build();
        }

        // 3. Verificar se o token está expirado
        long expirationTime = tokenEntity.getTimestamp("valid_to").toDate().getTime();
        long now = System.currentTimeMillis();
        if (now > expirationTime) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Token expired\"}")
                    .build();
        }

        // 4. Recuperar informações do token: usuário autenticado e role
        String authUsername = tokenEntity.getString("username");
        String authUserRole = tokenEntity.getString("role").toUpperCase();

        // 5. Validar o corpo da requisição
        if (data.getTargetUsername() == null || data.getTargetUsername().isBlank()) {
            return Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"Missing targetUsername in request body\"}")
                    .build();
        }

        // 6. Buscar o usuário alvo na datastore
        Key targetUserKey = userKeyFactory.newKey(data.getTargetUsername());
        Entity targetUser = datastore.get(targetUserKey);
        if (targetUser == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("{\"error\": \"Target user not found\"}")
                    .build();
        }

        String targetRole = targetUser.contains("role")
                ? targetUser.getString("role").toUpperCase()
                : "ENDUSER";

        // 7. Verificar permissão:
        // - ADMIN pode remover qualquer conta.
        // - BACKOFFICE pode remover se o target user tiver role ENDUSER ou PARTNER.
        boolean allowed = false;
        if ("ADMIN".equals(authUserRole)) {
            allowed = true;
        } else if ("BACKOFFICE".equals(authUserRole)) {
            if ("ENDUSER".equals(targetRole) || "PARTNER".equals(targetRole)) {
                allowed = true;
            }
        }
        if (!allowed) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Not enough privileges to remove this account\"}")
                    .build();
        }

        // 8. Remover o usuário alvo
        // Primeiro, deletar todos os tokens do usuário (buscando tokens pelo campo "username")
        Query<Key> queryTokens = Query.newKeyQueryBuilder()
                .setKind("AuthToken")
                .setFilter(PropertyFilter.eq("username", data.getTargetUsername()))
                .build();

        QueryResults<Key> tokenKeysResults = datastore.run(queryTokens);
        List<Key> tokenKeysToDelete = new ArrayList<>();
        while (tokenKeysResults.hasNext()) {
            tokenKeysToDelete.add(tokenKeysResults.next());
        }

        // Acrescentar a key do usuário para remoção
        tokenKeysToDelete.add(targetUserKey);

        datastore.delete(tokenKeysToDelete.toArray(new Key[0]));

        LOG.info("User " + data.getTargetUsername() + " removed by " + authUsername + " with role " + authUserRole);
        String msg = String.format("{\"message\": \"User '%s' removed successfully\"}", data.getTargetUsername());
        return Response.ok(msg).build();
    }
}
