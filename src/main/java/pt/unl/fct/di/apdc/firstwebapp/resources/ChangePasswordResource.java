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

    private static final Logger LOG = Logger.getLogger(ChangePasswordResource.class.getName());

    // Datastore
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    // KeyFactory para as entidades do tipo "User"
    private static final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");
    // KeyFactory para as entidades do token, Kind "AuthToken"
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");

    private final Gson g = new Gson();

    @POST
    public Response changePassword(@Context HttpHeaders headers, ChangePasswordData data) {
        // 1. Validação dos campos obrigatórios do input
        if (data.getOldPassword() == null || data.getOldPassword().isBlank() ||
                data.getNewPassword() == null || data.getNewPassword().isBlank() ||
                data.getConfirmPassword() == null || data.getConfirmPassword().isBlank()) {
            return Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"Missing required password fields\"}")
                    .build();
        }

        // Verifica se a nova senha coincide com a confirmação
        if (!data.getNewPassword().equals(data.getConfirmPassword())) {
            return Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"New password and confirmation do not match\"}")
                    .build();
        }

        // 2. Extração e validação do token no header "Authorization"
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

        // 3. Buscar a entidade do token na datastore
        Key tokenKey = tokenKeyFactory.newKey(tokenStr);
        Entity tokenEntity = datastore.get(tokenKey);
        if (tokenEntity == null) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Invalid token\"}")
                    .build();
        }

        // 4. Verificar se o token está expirado
        Timestamp validTo = tokenEntity.getTimestamp("valid_to");
        long now = System.currentTimeMillis();
        if (validTo == null || now > validTo.toDate().getTime()) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Token expired\"}")
                    .build();
        }

        // 5. Obter o username do token; assim o usuário só pode alterar a senha da própria conta
        String authUsername = tokenEntity.getString("username");

        // 6. Buscar a entidade do usuário na datastore
        Key userKey = userKeyFactory.newKey(authUsername);
        Entity userEntity = datastore.get(userKey);
        if (userEntity == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("{\"error\": \"User account not found\"}")
                    .build();
        }

        // 7. Validar a senha atual
        String storedHashedPassword = userEntity.getString("user_pwd");
        if (!storedHashedPassword.equals(DigestUtils.sha512Hex(data.getOldPassword()))) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Incorrect current password\"}")
                    .build();
        }

        // 8. Atualizar a senha
        String newHashedPassword = DigestUtils.sha512Hex(data.getNewPassword());
        Entity updatedUser = Entity.newBuilder(userEntity)
                .set("user_pwd", newHashedPassword)
                .build();
        datastore.put(updatedUser);

        LOG.info("Password changed successfully for user: " + authUsername);
        return Response.ok("{\"message\": \"Password changed successfully\"}").build();
    }
}
