package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.digest.DigestUtils;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreException;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Transaction;
import com.google.gson.Gson;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import pt.unl.fct.di.apdc.firstwebapp.util.RegisterData;

@Path("/register")
public class RegisterResource {

	private static final Logger LOG = Logger.getLogger(RegisterResource.class.getName());
	private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private final Gson g = new Gson();

	public RegisterResource() { }

	@POST
	@Path("/v3")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response registerUser(RegisterData data) {
		LOG.fine("Tentativa de registo do utilizador: " + data.username);


		if (!data.validRegistration()) {
			return Response.status(Status.BAD_REQUEST)
					.entity("Parâmetros em falta ou incorretos.")
					.build();
		}

		Transaction txn = datastore.newTransaction();
		try {
			Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.username);
			Entity existingUser = txn.get(userKey);


			if (existingUser != null) {
				txn.rollback();
				return Response.status(Status.CONFLICT)
						.entity("Utilizador já existe.")
						.build();
			}


			Entity.Builder builder = Entity.newBuilder(userKey)
					.set("user_name", data.name)
					.set("user_email", data.email)
					.set("user_pwd", DigestUtils.sha512Hex(data.password))
					.set("user_creation_time", Timestamp.now())
					.set("user_phone", data.phone)
					.set("account_profile", data.profile)
					.set("role", "enduser")
					.set("account_status", "DESATIVADA");

			if (data.cc_number != null && !data.cc_number.isBlank()) {
				builder.set("cc_number", data.cc_number);
			}
			if (data.nif != null && !data.nif.isBlank()) {
				builder.set("nif", data.nif);
			}
			if (data.job_entity != null && !data.job_entity.isBlank()) {
				builder.set("job_entity", data.job_entity);
			}
			if (data.job != null && !data.job.isBlank()) {
				builder.set("job", data.job);
			}
			if (data.address != null && !data.address.isBlank()) {
				builder.set("address", data.address);
			}
			if (data.job_entity_nif != null && !data.job_entity_nif.isBlank()) {
				builder.set("job_entity_nif", data.job_entity_nif);
			}

			Entity user = builder.build();

			txn.put(user);
			txn.commit();
			LOG.info("Utilizador registado: " + data.username);
			return Response.ok().build();
		} catch (DatastoreException e) {
			LOG.log(Level.SEVERE, "Erro no registo: " + e.toString(), e);
			return Response.status(Status.INTERNAL_SERVER_ERROR)
					.entity("Erro ao registar utilizador: " + e.getMessage())
					.build();
		} finally {
			if (txn.isActive()) {
				txn.rollback();
			}
		}
	}
}
