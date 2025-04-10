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

    // KeyFactory para as entidades de Token – a partir do Kind "AuthToken"
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");

    // KeyFactory para usuários (Kind "User")
    private static final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");

    private final Gson g = new Gson();

    /**
     * Endpoint para listar usuários.
     * A autorização é feita via token Bearer (extraído do cabeçalho Authorization).
     * O comportamento da listagem depende do role do utilizador autenticado.
     */
    @POST
    public Response listUsers(@Context HttpHeaders headers, ListUsersData inputData) {
        // 1. Obter token do header
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

        // 2. Buscar a entidade do token (chave igual ao token string, neste exemplo)
        Key tokenKey = tokenKeyFactory.newKey(tokenStr);
        Entity tokenEntity = datastore.get(tokenKey);
        if (tokenEntity == null) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Invalid token\"}")
                    .build();
        }

        // 3. Verificar validade do token
        Timestamp validTo = tokenEntity.contains("valid_to") ? tokenEntity.getTimestamp("valid_to") : null;
        if (validTo == null || System.currentTimeMillis() > validTo.toDate().getTime()) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Token expired\"}")
                    .build();
        }

        // 4. Recuperar informações do token
        String authUsername = tokenEntity.getString("username");
        String authUserRole = tokenEntity.getString("role").toUpperCase();

        // 5. Determinar os filtros para a consulta baseada no role do usuário autenticado
        // Variáveis para construção do filtro.
        List<StructuredQuery.Filter> filters = new ArrayList<>();

        if ("ENDUSER".equals(authUserRole)) {
            // ENDUSER: listar apenas contas com role "ENDUSER", com perfil público e estado "ATIVADA"
            filters.add(PropertyFilter.eq("role", "ENDUSER"));
            filters.add(PropertyFilter.eq("account_profile", "público"));
            filters.add(PropertyFilter.eq("account_status", "ATIVADA"));
        } else if ("BACKOFFICE".equals(authUserRole)) {
            // BACKOFFICE: pode listar somente contas de usuários com role "ENDUSER" (independente do perfil e estado)
            filters.add(PropertyFilter.eq("role", "ENDUSER"));
        } else if ("ADMIN".equals(authUserRole)) {
            // ADMIN: não filtra; lista todos os usuários
        } else {
            // Outros: não têm permissão para visualizar a lista de usuários
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Not enough privileges to list users\"}")
                    .build();
        }

        // Construção da consulta
        StructuredQuery<Entity> query;
        if (!filters.isEmpty()) {
            StructuredQuery.Filter[] filterArray = filters.toArray(new StructuredQuery.Filter[filters.size()]);
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

        // 6. Montar a resposta de acordo com o role do utilizador autenticado.
        // Para ENDUSER: retornar apenas username, email e nome.
        // Para BACKOFFICE: retornar todos os atributos da entidade, mas apenas para usuários com role "ENDUSER"
        // Para ADMIN: retornar todos os atributos de todas as contas.
        List<Map<String, String>> usersList = new ArrayList<>();
        while (results.hasNext()) {
            Entity userEntity = results.next();
            // Se BACKOFFICE, garantir que a conta do usuário tem role ENDUSER
            if ("BACKOFFICE".equals(authUserRole)) {
                String userRole = userEntity.contains("role") ? userEntity.getString("role").toUpperCase() : "ENDUSER";
                if (!"ENDUSER".equals(userRole)) {
                    continue;
                }
            }
            Map<String, String> userMap = new HashMap<>();

            // Sempre incluir o username (a chave da entidade)
            String username = userEntity.getKey().getName();
            userMap.put("username", username != null ? username : "NOT DEFINED");

            // Dependendo do role do solicitante, filtramos os atributos
            if ("ENDUSER".equals(authUserRole)) {
                // Retorna apenas email e nome.
                String email = userEntity.contains("user_email") ? userEntity.getString("user_email") : "NOT DEFINED";
                String name = userEntity.contains("user_name") ? userEntity.getString("user_name") : "NOT DEFINED";
                userMap.put("email", email);
                userMap.put("name", name);
            } else {
                // BACKOFFICE e ADMIN retornam todos os atributos relevantes.
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
