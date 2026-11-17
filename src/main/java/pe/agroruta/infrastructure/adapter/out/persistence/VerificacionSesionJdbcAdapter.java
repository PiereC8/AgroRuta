package pe.agroruta.infrastructure.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.ReglasVerificacion;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.out.VerificacionSesionPort;
import pe.agroruta.infrastructure.config.TenantContext;

/** RFF-01: guarda los desafíos de la verificación en dos pasos en la base del negocio (tabla verificacion_sesion). */
@Component
public class VerificacionSesionJdbcAdapter implements VerificacionSesionPort {

    private final JdbcTemplate jdbc;

    public VerificacionSesionJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public Optional<String> correoDe(String identificadorNegocio, Long idUsuario) {
        return TenantContext.ejecutarEn(identificadorNegocio, () -> jdbc.queryForList(
                "SELECT correo_usuario FROM usuario WHERE id_usuario = ? AND correo_usuario IS NOT NULL "
                        + "AND correo_usuario <> ''", String.class, idUsuario).stream().findFirst());
    }

    @Override
    public int codigosDesde(String identificadorNegocio, Long idUsuario, Instant desde) {
        return TenantContext.ejecutarEn(identificadorNegocio, () -> {
            Integer total = jdbc.queryForObject("SELECT COALESCE(SUM(envios), 0) FROM verificacion_sesion "
                    + "WHERE id_usuario = ? AND ultimo_envio >= ?", Integer.class, idUsuario, Timestamp.from(desde));
            return total == null ? 0 : total;
        });
    }

    @Override
    public void crear(String identificadorNegocio, Long idUsuario, String hashDesafio, String hashCodigo,
                      Instant ahora, Instant vence) {
        Timestamp momento = Timestamp.from(ahora);
        TenantContext.ejecutarEn(identificadorNegocio, () -> {
            // Un solo desafío vivo por usuario: el inicio de sesión nuevo anula los anteriores.
            jdbc.update("UPDATE verificacion_sesion SET fecha_uso = ? WHERE id_usuario = ? AND fecha_uso IS NULL",
                    momento, idUsuario);
            return jdbc.update("INSERT INTO verificacion_sesion (id_usuario, hash_desafio, hash_codigo, "
                            + "fecha_creacion, fecha_vencimiento, ultimo_envio) VALUES (?, ?, ?, ?, ?, ?)",
                    idUsuario, hashDesafio, hashCodigo, momento, Timestamp.from(vence), momento);
        });
    }

    @Override
    public Optional<Desafio> buscar(String identificadorNegocio, String hashDesafio) {
        return TenantContext.ejecutarEn(identificadorNegocio, () -> jdbc.query(
                "SELECT v.id_verificacion, v.id_usuario, u.nombre_usuario, u.rol_usuario, u.usuario_activo, "
                        + "v.hash_codigo, v.fecha_vencimiento, v.ultimo_envio, v.envios, v.intentos_fallidos, "
                        + "v.fecha_uso FROM verificacion_sesion v JOIN usuario u ON u.id_usuario = v.id_usuario "
                        + "WHERE v.hash_desafio = ?",
                (rs, fila) -> new Desafio(rs.getLong("id_verificacion"), rs.getLong("id_usuario"),
                        rs.getString("nombre_usuario"), Rol.valueOf(rs.getString("rol_usuario")),
                        rs.getBoolean("usuario_activo"), rs.getString("hash_codigo"),
                        rs.getTimestamp("fecha_vencimiento").toInstant(), rs.getTimestamp("ultimo_envio").toInstant(),
                        rs.getInt("envios"), rs.getInt("intentos_fallidos"), rs.getTimestamp("fecha_uso") != null),
                hashDesafio).stream().findFirst());
    }

    @Override
    public void sumarIntentoFallido(String identificadorNegocio, Long idVerificacion) {
        TenantContext.ejecutarEn(identificadorNegocio, () -> jdbc.update(
                "UPDATE verificacion_sesion SET intentos_fallidos = intentos_fallidos + 1 WHERE id_verificacion = ?",
                idVerificacion));
    }

    @Override
    public void reemplazarCodigo(String identificadorNegocio, Long idVerificacion, String hashCodigo, Instant ahora,
                                 Instant vence) {
        TenantContext.ejecutarEn(identificadorNegocio, () -> jdbc.update(
                "UPDATE verificacion_sesion SET hash_codigo = ?, ultimo_envio = ?, fecha_vencimiento = ?, "
                        + "envios = envios + 1, intentos_fallidos = 0 WHERE id_verificacion = ? AND fecha_uso IS NULL",
                hashCodigo, Timestamp.from(ahora), Timestamp.from(vence), idVerificacion));
    }

    @Override
    public boolean consumir(String identificadorNegocio, Long idVerificacion, Instant ahora) {
        Timestamp momento = Timestamp.from(ahora);
        // Las condiciones se repiten aquí: si dos peticiones usan el mismo código a la vez, solo una lo marca.
        return TenantContext.ejecutarEn(identificadorNegocio, () -> jdbc.update(
                "UPDATE verificacion_sesion SET fecha_uso = ? WHERE id_verificacion = ? AND fecha_uso IS NULL "
                        + "AND fecha_vencimiento > ? AND intentos_fallidos < ?",
                momento, idVerificacion, momento, ReglasVerificacion.MAXIMO_INTENTOS)) == 1;
    }
}
