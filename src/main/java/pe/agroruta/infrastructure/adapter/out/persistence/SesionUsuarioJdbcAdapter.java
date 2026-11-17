package pe.agroruta.infrastructure.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.port.out.SesionUsuarioPort;
import pe.agroruta.infrastructure.config.TenantContext;

/**
 * RFF-01 (sesión única): guarda en la tabla usuario de cada negocio la huella de la sesión abierta y su último uso.
 * Cómo: cada operación es un solo UPDATE con sus condiciones, así dos dispositivos que entran a la vez no ganan los dos.
 */
@Component
public class SesionUsuarioJdbcAdapter implements SesionUsuarioPort {

    private final JdbcTemplate jdbc;

    public SesionUsuarioJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public Optional<Instant> ultimoUso(String identificadorNegocio, String nombreUsuario) {
        return TenantContext.ejecutarEn(identificadorNegocio, () -> jdbc.queryForList(
                "SELECT sesion_ultimo_uso FROM usuario WHERE nombre_usuario = ? AND sesion_hash IS NOT NULL "
                        + "AND sesion_ultimo_uso IS NOT NULL", Timestamp.class, nombreUsuario)
                .stream().findFirst().map(Timestamp::toInstant));
    }

    @Override
    public boolean abrirSiLibre(String identificadorNegocio, String nombreUsuario, String hashSesion, Instant ahora,
                                Instant libreAntesDe) {
        return TenantContext.ejecutarEn(identificadorNegocio, () -> jdbc.update(
                "UPDATE usuario SET sesion_hash = ?, sesion_ultimo_uso = ? WHERE nombre_usuario = ? "
                        + "AND (sesion_hash IS NULL OR sesion_ultimo_uso IS NULL OR sesion_ultimo_uso <= ?)",
                hashSesion, Timestamp.from(ahora), nombreUsuario, Timestamp.from(libreAntesDe))) == 1;
    }

    @Override
    public boolean usar(String identificadorNegocio, String nombreUsuario, String hashSesion, Instant ahora,
                        Instant libreAntesDe) {
        return TenantContext.ejecutarEn(identificadorNegocio, () -> jdbc.update(
                "UPDATE usuario SET sesion_ultimo_uso = ? WHERE nombre_usuario = ? AND sesion_hash = ? "
                        + "AND usuario_activo = TRUE AND sesion_ultimo_uso > ?",
                Timestamp.from(ahora), nombreUsuario, hashSesion, Timestamp.from(libreAntesDe))) == 1;
    }

    @Override
    public void cerrar(String identificadorNegocio, String nombreUsuario, String hashSesion) {
        TenantContext.ejecutarEn(identificadorNegocio, () -> jdbc.update(
                "UPDATE usuario SET sesion_hash = NULL, sesion_ultimo_uso = NULL WHERE nombre_usuario = ? "
                        + "AND sesion_hash = ?", nombreUsuario, hashSesion));
    }
}
