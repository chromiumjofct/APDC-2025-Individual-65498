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
import pt.unl.fct.di.apdc.firstwebapp.util.ChangeAccountStateData;

@Path("/changeaccountstate")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
@Consumes(MediaType.APPLICATION_JSON)
public class ChangeAccountStateResource {

    private static final Logger LOG = Logger.getLogger(ChangeAccountStateResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    // KeyFactory para a entidade de usuário (User)
    private static final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");
    // KeyFactory para a entidade de token (AuthToken)
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");

    private final Gson g = new Gson();

    /**
     * Endpoint para mudança de estado de conta.
     * <p>
     * Requisitos:
     * - O header "Authorization" deve conter: "Bearer <token>"
     * - O corpo JSON deve conter:
     * - targetUsername: o nome de usuário cujo estado se deseja alterar
     * - newState: o novo estado a definir (ex.: "ATIVADA" ou "DESATIVADA")
     * <p>
     * Exemplos de validação de permissão:
     * - ADMIN pode mudar qualquer estado.
     * - BACKOFFICE pode mudar contas se o novo estado for "ATIVADA" ou "DESATIVADA".
     */
    @POST
    public Response changeAccountState(@Context HttpHeaders headers, ChangeAccountStateData data) {
        // Obtém o token do header "Authorization"
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

        // Procura o token na datastore – aqui, supõe-se que o key da entidade AuthToken é o próprio token
        Key tokenKey = tokenKeyFactory.newKey(tokenStr);
        Entity tokenEntity = datastore.get(tokenKey);
        if (tokenEntity == null) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\":\"Invalid token\"}")
                    .build();
        }

        // Verifica se o token ainda é válido
        Timestamp validToTimestamp = tokenEntity.getTimestamp("valid_to");
        long expirationMillis = validToTimestamp.toDate().getTime();
        if (System.currentTimeMillis() > expirationMillis) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\":\"Token expired\"}")
                    .build();
        }

        // Recupera o username e o role do token
        String authUsername = tokenEntity.getString("username");
        String authUserRole = tokenEntity.getString("role");

        // Recupera o usuário alvo a partir de targetUsername
        Key targetUserKey = userKeyFactory.newKey(data.getTargetUsername());
        Entity targetUser = datastore.get(targetUserKey);
        if (targetUser == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("{\"error\":\"Target user not found\"}")
                    .build();
        }

        String currentState = targetUser.getString("account_status");
        String newState = data.getNewState().toUpperCase();

        // Valida se o novo estado é um dos valores permitidos
        if (data.getTargetUsername() == null || !data.isValidState()) {
            return Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"Dados de entrada inválidos. Verifique targetUsername e newState.\"}")
                    .build();
        }

        // Verifica permissões de acordo com o role do usuário autenticado
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

        // Se o estado atual já é igual ao novo estado, não é necessário atualizar
        if (currentState.equals(newState)) {
            return Response.status(Status.OK)
                    .entity("{\"message\":\"Account state is already " + newState + "\"}")
                    .build();
        }

        // Atualiza o campo 'account_status' do usuário alvo
        Entity updatedUser = Entity.newBuilder(targetUser)
                .set("account_status", newState)
                .build();
        datastore.put(updatedUser);
        LOG.info("Account state changed for user " + data.getTargetUsername() +
                " from " + currentState + " to " + newState +
                " by " + authUsername);

        // Retorna a resposta com os dados atualizados
        String jsonResponse = String.format("{\"username\": \"%s\", \"newAccountState\": \"%s\"}",
                updatedUser.getKey().getName(), updatedUser.getString("account_status"));
        return Response.ok(jsonResponse).build();
    }
}
