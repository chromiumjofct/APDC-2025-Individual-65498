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

    // Instância do Datastore
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    // KeyFactory para as entidades do token: Kind "AuthToken"
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");

    private final Gson g = new Gson();

    /**
     * Endpoint para Logout de sessão.
     * O cliente deve enviar o token no header "Authorization" no formato:
     *     Authorization: Bearer <token>
     *
     * Se o token existir e não estiver expirado, ele é removido da datastore.
     * Após o logout, este token não poderá mais ser usado.
     */
    @POST
    public Response logout(@Context HttpHeaders headers) {
        // 1. Extrair o token do header "Authorization"
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

        // 2. Buscar a entidade do token na Datastore
        Key tokenKey = tokenKeyFactory.newKey(tokenStr);
        Entity tokenEntity = datastore.get(tokenKey);
        if (tokenEntity == null) {
            // Podemos considerar o logout como "já efetuado" se o token não for encontrado
            return Response.status(Status.OK)
                    .entity("{\"message\": \"Token not found or already revoked\"}")
                    .build();
        }

        // 3. (Opcional) Poderíamos verificar a expiração do token, se essa informação estiver armazenada;
        //    para este exemplo, vamos apenas removê-lo.

        datastore.delete(tokenKey);
        LOG.info("Token " + tokenStr + " revoked successfully.");
        return Response.ok("{\"message\": \"Logout successful\"}").build();
    }
}
