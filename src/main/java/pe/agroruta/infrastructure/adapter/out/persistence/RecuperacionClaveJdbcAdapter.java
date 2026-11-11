package pe.agroruta.infrastructure.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.port.out.RecuperacionClavePort;
import pe.agroruta.infrastructure.config.TenantContext;

@Component
public class RecuperacionClaveJdbcAdapter implements RecuperacionClavePort {

    private final JdbcTemplate jdbc;

    public RecuperacionClaveJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public Optional<Cuenta> buscarCuenta(String identificadorNegocio, String nombreUsuario) {
        return TenantContext.ejecutarEn(identificadorNegocio, () -> jdbc.query(
                "SELECT id_usuario, nombre_usuario, correo_usuario, usuario_activo FROM usuario "
                        + "WHERE nombre_usuario = ?",
                (rs, fila) -> new Cuenta(rs.getLong("id_usuario"), rs.getString("nombre_usuario"),
                        rs.getString("correo_usuario"), rs.getBoolean("usuario_activo")),
                nombreUsuario).stream().findFirst());
    }

    @Override
    public int solicitudesDesde(String identificadorNegocio, Long idUsuario, Instant desde) {
        return TenantContext.ejecutarEn(identificadorNegocio, () -> {
            Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM recuperacion_clave "
                    + "WHERE id_usuario = ? AND fecha_creacion >= ?", Integer.class, idUsuario, Timestamp.from(desde));
            return total == null ? 0 : total;
        });
    }

    @Override
    public void guardarSolicitud(String identificadorNegocio, Long idUsuario, String hashToken, Instant creada,
                                 Instant vence) {
        TenantContext.ejecutarEn(identificadorNegocio, () -> jdbc.update(
                "INSERT INTO recuperacion_clave (id_usuario, hash_token, fecha_creacion, fecha_vencimiento) "
                        + "VALUES (?, ?, ?, ?)", idUsuario, hashToken, Timestamp.from(creada), Timestamp.from(vence)));
    }

    @Override
    public Optional<Long> consumirToken(String identificadorNegocio, String hashToken, Instant ahora) {
        Timestamp momento = Timestamp.from(ahora);
        return TenantContext.ejecutarEn(identificadorNegocio, () -> {
            List<Long> usuarios = jdbc.queryForList("SELECT r.id_usuario FROM recuperacion_clave r "
                    + "JOIN usuario u ON u.id_usuario = r.id_usuario WHERE r.hash_token = ? AND r.fecha_uso IS NULL "
                    + "AND r.fecha_vencimiento > ? AND u.usuario_activo = TRUE", Long.class, hashToken, momento);
            if (usuarios.isEmpty()) {
                return Optional.<Long>empty();
            }
            // La condición se repite en el UPDATE: si dos peticiones usan el mismo enlace a la vez, solo una lo marca.
            int marcados = jdbc.update("UPDATE recuperacion_clave SET fecha_uso = ? WHERE hash_token = ? "
                    + "AND fecha_uso IS NULL AND fecha_vencimiento > ?", momento, hashToken, momento);
            if (marcados != 1) {
                return Optional.<Long>empty();
            }
            Long idUsuario = usuarios.get(0);
            jdbc.update("UPDATE recuperacion_clave SET fecha_uso = ? WHERE id_usuario = ? AND fecha_uso IS NULL",
                    momento, idUsuario);
            return Optional.of(idUsuario);
        });
    }

    @Override
    public void cambiarClave(String identificadorNegocio, Long idUsuario, String hashClave) {
        TenantContext.ejecutarEn(identificadorNegocio, () -> jdbc.update("UPDATE usuario SET hash_clave = ?, "
                + "intentos_fallidos_sesion = 0, fecha_bloqueo_hasta = NULL WHERE id_usuario = ?", hashClave, idUsuario));
    }
}
