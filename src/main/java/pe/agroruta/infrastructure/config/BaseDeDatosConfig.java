package pe.agroruta.infrastructure.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class BaseDeDatosConfig {

    @Bean
    NombresBd nombresBd(@Value("${agroruta.bd.url}") String url,
                        @Value("${agroruta.bd.prefijo}") String prefijo,
                        @Value("${agroruta.bd.sufijo}") String sufijo) {
        return new NombresBd(url, prefijo, sufijo);
    }

    @Bean(destroyMethod = "close")
    HikariDataSource plataformaDataSource(NombresBd nombres,
                                          @Value("${agroruta.bd.usuario}") String usuario,
                                          @Value("${agroruta.bd.clave}") String clave) {
        HikariDataSource pool = new HikariDataSource();
        pool.setJdbcUrl(NombresBd.creandoBaseSiFalta(nombres.urlPlataforma()));
        pool.setUsername(usuario);
        pool.setPassword(clave);
        pool.setMaximumPoolSize(5);
        pool.setMinimumIdle(0);
        pool.setPoolName("plataforma");
        return pool;
    }

    @Bean(initMethod = "migrate")
    Flyway flywayPlataforma(@Qualifier("plataformaDataSource") DataSource plataforma) {
        return Flyway.configure()
                .dataSource(plataforma)
                .locations("classpath:db/migration/plataforma")
                .load();
    }

    @Bean
    JdbcTemplate plataformaJdbc(@Qualifier("plataformaDataSource") DataSource plataforma) {
        return new JdbcTemplate(plataforma);
    }

    @Bean
    PlatformTransactionManager transaccionesNegocios(@Qualifier("negociosDataSource") DataSource negocios) {
        return new DataSourceTransactionManager(negocios);
    }

    @Bean
    @Primary
    DataSource negociosDataSource(NombresBd nombres,
                                  @Value("${agroruta.bd.usuario}") String usuario,
                                  @Value("${agroruta.bd.clave}") String clave) {
        return new TenantRoutingDataSource(identificadorNegocio -> {
            HikariDataSource pool = new HikariDataSource();
            pool.setJdbcUrl(nombres.urlDeIdentificador(identificadorNegocio));
            pool.setUsername(usuario);
            pool.setPassword(clave);
            // Pocas conexiones por negocio y que se cierren al minuto sin uso: las tareas que recorren todos los
            // negocios no dejan conexiones abiertas de más en MySQL.
            pool.setMaximumPoolSize(3);
            pool.setMinimumIdle(0);
            pool.setIdleTimeout(60_000);
            pool.setPoolName("negocio-" + identificadorNegocio);
            return pool;
        });
    }
}
