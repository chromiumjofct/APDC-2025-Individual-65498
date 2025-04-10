package pt.unl.fct.di.apdc.firstwebapp.initializers;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.logging.Logger;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.Timestamp;
import org.apache.commons.codec.digest.DigestUtils;

@WebListener
public class AppInitializer implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(AppInitializer.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOG.info("Inicializando a aplicação. Verificando a existência da conta 'root'...");

        Key rootKey = datastore.newKeyFactory().setKind("User").newKey("root");
        Entity rootUser = datastore.get(rootKey);

        if (rootUser == null) {
            // Criação da conta "root"
            rootUser = Entity.newBuilder(rootKey)
                    .set("user_name", "Administrador")
                    .set("user_pwd", DigestUtils.sha512Hex("suaSenhaRoot")) // Substitua "suaSenhaRoot" pela senha desejada
                    .set("user_email", "root@seuDominio.com")
                    .set("user_creation_time", Timestamp.now())
                    .set("role", "ADMIN")
                    .set("account_status", "ATIVADA")
                    .build();

            datastore.put(rootUser);
            LOG.info("Conta 'root' criada com sucesso.");
        } else {
            LOG.info("Conta 'root' já existe.");
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOG.info("Contexto da aplicação encerrado.");
    }
}
