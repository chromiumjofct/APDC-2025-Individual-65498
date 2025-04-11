package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.Date;
import java.util.logging.Logger;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
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

    private static final Logger LOG = Logger.getLogger(CreateWorkSheetResource.class.getName());

    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");
    private static final KeyFactory workSheetKeyFactory = datastore.newKeyFactory().setKind("WorkSheet");

    private final Gson g = new Gson();

    @POST
    @Path("/create")
    public Response createOrUpdateWorkSheet(@Context HttpHeaders headers, CreateWorkSheetData data) {

        // 1. Extrair token do cabeçalho Authorization
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

        // 2. Buscar a entidade do token
        Key tokenKey = tokenKeyFactory.newKey(tokenStr);
        Entity tokenEntity = datastore.get(tokenKey);
        if (tokenEntity == null) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Invalid token: not found in datastore\"}")
                    .build();
        }

        // 3. Verificar se o token está expirado (valid_to no formato Timestamp)
        Timestamp validToTs = tokenEntity.getTimestamp("valid_to");
        long now = System.currentTimeMillis();
        if (validToTs != null && now > validToTs.toDate().getTime()) {
            return Response.status(Status.FORBIDDEN)
                    .entity("{\"error\": \"Token expired\"}")
                    .build();
        }

        // 4. Recuperar informações do token: usuário autenticado e role
        String authUsername = tokenEntity.getString("username");
        String authUserRole = tokenEntity.getString("role").toUpperCase();

        // 5. Validação básica dos atributos obrigatórios
        if (data.reference == null || data.reference.isBlank() ||
                data.description == null || data.description.isBlank() ||
                data.targetType == null   || data.targetType.isBlank() ||
                data.awardingStatus == null || data.awardingStatus.isBlank()) {

            return Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"Missing mandatory fields: reference, description, targetType, awardingStatus\"}")
                    .build();
        }

        // Normalizamos awardingStatus para maiúsculo
        String awardingStatus = data.awardingStatus.toUpperCase();

        // 6. Obter ou criar a key da WorkSheet no Datastore
        Key workSheetKey = workSheetKeyFactory.newKey(data.reference);

        // 7. Buscamos se já existe uma WorkSheet com essa referência
        Entity existingWorksheet = datastore.get(workSheetKey);

        // 8. Verificar permissões
        // Se a worksheet não existe, somente BACKOFFICE pode criar
        if (existingWorksheet == null) {
            if (!"BACKOFFICE".equals(authUserRole)) {
                return Response.status(Status.FORBIDDEN)
                        .entity("{\"error\": \"Only BACKOFFICE can create new worksheets\"}")
                        .build();
            }
        } else {
            // Se a worksheet já existe, PARTNER pode atualizar somente o estado da obra e observações
            // se for a parceira atribuída.
            // BACKOFFICE pode atualizar qualquer campo.
        }

        // Vamos construir ou atualizar a entidade com a transação (opcional)
        Transaction txn = datastore.newTransaction();
        try {
            Entity.Builder builder;
            if (existingWorksheet == null) {
                // Criando nova WorkSheet
                builder = Entity.newBuilder(workSheetKey)
                        .set("reference", data.reference)
                        .set("description", data.description)
                        .set("targetType", data.targetType)
                        .set("awardingStatus", awardingStatus);

                // Campos de adjudicação só se awardingStatus = "ADJUDICADO" e role=BACKOFFICE
                if ("ADJUDICADO".equals(awardingStatus)) {
                    // Preenche dados de adjudicação
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
                    // awardingStatus = "NÃO ADJUDICADO"
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
                // Atualizando WorkSheet existente
                // Carregar dados atuais
                builder = Entity.newBuilder(existingWorksheet);

                // Verificamos se o usuário é BACKOFFICE
                if ("BACKOFFICE".equals(authUserRole)) {
                    // Pode atualizar tudo
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
                        // Se não houver state, assumimos "NÃO INICIADO" caso já não esteja setado
                        String currentState = existingWorksheet.contains("workState")
                                ? existingWorksheet.getString("workState") : "";
                        builder.set("workState", data.workState == null ? currentState : data.workState);
                        // Observations
                        String currentObs = existingWorksheet.contains("observations")
                                ? existingWorksheet.getString("observations") : "";
                        builder.set("observations", data.observations == null ? currentObs : data.observations);

                    } else {
                        // awardingStatus = "NÃO ADJUDICADO" => zera os campos de adjudicação
                        builder.set("awardingDate", NullValue.of())
                                .set("startDate", NullValue.of())
                                .set("endDate", NullValue.of())
                                .set("partnerAccount", "")
                                .set("awardingEntity", "")
                                .set("awardingNif", "")
                                .set("workState", "")
                                .set("observations", "");
                    }
                }
                // Se for PARTNER, só pode atualizar "workState" e "observations"
                else if ("PARTNER".equals(authUserRole)) {
                    // Somente se awardingStatus = "ADJUDICADO" e partnerAccount = authUsername
                    String currentAwardStatus = existingWorksheet.getString("awardingStatus");
                    String currentPartner = existingWorksheet.getString("partnerAccount");
                    if (!"ADJUDICADO".equalsIgnoreCase(currentAwardStatus) ||
                            !currentPartner.equals(authUsername)) {
                        return Response.status(Status.FORBIDDEN)
                                .entity("{\"error\": \"You are not the partner assigned or awardingStatus != ADJUDICADO\"}")
                                .build();
                    }
                    // Atualiza somente o estado e as observações
                    String newState = data.workState; // "NÃO INICIADO", "EM CURSO", "CONCLUÍDO"
                    if (newState != null && !newState.isBlank()) {
                        builder.set("workState", newState);
                    }
                    if (data.observations != null) {
                        builder.set("observations", data.observations);
                    }
                } else {
                    // Se for outro role (ENDUSER, ADMIN, etc.), decide se permite ou não
                    // Aqui, assumimos que não pode modificar
                    return Response.status(Status.FORBIDDEN)
                            .entity("{\"error\": \"This role cannot modify existing worksheets\"}")
                            .build();
                }
            }

            Entity finalWorksheet = builder.build();
            txn.put(finalWorksheet);
            txn.commit();

            // Retorna a entidade em JSON
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
     * Converte a entidade em uma representação JSON básica.
     * Pode customizar conforme desejar.
     */
    private Object entityToWorkSheetResponse(Entity e) {
        // Monta um Map ou um objeto anônimo
        // Observando que datas armazenadas como Timestamp podem ser convertidas para Date
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

            // Exemplo de como extrair timestamps
            public Date awardingDate       = e.contains("awardingDate") && !e.isNull("awardingDate")
                    ? e.getTimestamp("awardingDate").toDate()
                    : null;
            public Date startDate          = e.contains("startDate") && !e.isNull("startDate")
                    ? e.getTimestamp("startDate").toDate()
                    : null;
            public Date endDate            = e.contains("endDate") && !e.isNull("endDate")
                    ? e.getTimestamp("endDate").toDate()
                    : null;
        };
    }
}
