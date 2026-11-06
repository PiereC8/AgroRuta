package pe.agroruta.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.port.out.BaseNegocioPort;
import pe.agroruta.infrastructure.config.NombresBd;

@Component
public class BaseNegocioAdapter implements BaseNegocioPort {

    private final JdbcTemplate jdbc;
    private final NombresBd nombres;
    private final String usuario;
    private final String clave;

    public BaseNegocioAdapter(@Qualifier("plataformaJdbc") JdbcTemplate jdbc,
                              NombresBd nombres,
                              @Value("${agroruta.bd.usuario}") String usuario,
                              @Value("${agroruta.bd.clave}") String clave) {
        this.jdbc = jdbc;
        this.nombres = nombres;
        this.usuario = usuario;
        this.clave = clave;
    }

    @Override
    public void crearYMigrar(String slug) {
        // El slug ya fue validado por el dominio (solo minúsculas, números y guion bajo).
        jdbc.execute("CREATE DATABASE IF NOT EXISTS `" + nombres.deSlug(slug)
                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        Flyway.configure()
                .dataSource(nombres.urlDeSlug(slug), usuario, clave)
                .locations("classpath:db/migration/negocio")
                .load()
                .migrate();
    }
}
