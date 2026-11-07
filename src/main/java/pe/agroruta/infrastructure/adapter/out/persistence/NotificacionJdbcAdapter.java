package pe.agroruta.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.EstadoNotificacion;
import pe.agroruta.domain.model.Notificacion;
import pe.agroruta.domain.model.TipoAviso;
import pe.agroruta.domain.port.out.NotificacionRepositoryPort;

@Component
public class NotificacionJdbcAdapter implements NotificacionRepositoryPort {

    private static final String COLUMNAS = "id_notificacion, clave_unica, tipo_aviso, correo_destino, asunto, cuerpo, "
            + "estado, intentos, ultimo_error, fecha_creacion, proximo_intento, fecha_envio";

    private static final RowMapper<Notificacion> FILA = (rs, fila) -> new Notificacion(
            rs.getLong("id_notificacion"),
            rs.getString("clave_unica"),
            TipoAviso.valueOf(rs.getString("tipo_aviso")),
            rs.getString("correo_destino"),
            rs.getString("asunto"),
            rs.getString("cuerpo"),
            EstadoNotificacion.valueOf(rs.getString("estado")),
            rs.getInt("intentos"),
            rs.getString("ultimo_error"),
            Sql.instante(rs.getTimestamp("fecha_creacion")),
            Sql.instante(rs.getTimestamp("proximo_intento")),
            Sql.instante(rs.getTimestamp("fecha_envio")));

    private final JdbcTemplate jdbc;

    public NotificacionJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public boolean encolar(Notificacion n) {
        try {
            jdbc.update("INSERT INTO notificacion (clave_unica, tipo_aviso, correo_destino, asunto, cuerpo, estado, "
                            + "intentos, ultimo_error, fecha_creacion, proximo_intento, fecha_envio) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    n.claveUnica(), n.tipo().name(), n.correoDestino(), n.asunto(), n.cuerpo(), n.estado().name(),
                    n.intentos(), n.ultimoError(), Sql.marca(n.fechaCreacion()), Sql.marca(n.proximoIntento()),
                    Sql.marca(n.fechaEnvio()));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public List<Notificacion> pendientes(Instant ahora, int limite) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM notificacion WHERE estado = 'PENDIENTE' "
                + "AND proximo_intento <= ? ORDER BY fecha_creacion, id_notificacion LIMIT ?", FILA,
                Sql.marca(ahora), limite);
    }

    @Override
    public void guardar(Notificacion n) {
        jdbc.update("UPDATE notificacion SET estado = ?, intentos = ?, ultimo_error = ?, proximo_intento = ?, "
                        + "fecha_envio = ? WHERE id_notificacion = ?", n.estado().name(), n.intentos(),
                n.ultimoError(), Sql.marca(n.proximoIntento()), Sql.marca(n.fechaEnvio()), n.idNotificacion());
    }

    @Override
    public List<Notificacion> recientes(int limite) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM notificacion ORDER BY fecha_creacion DESC, "
                + "id_notificacion DESC LIMIT ?", FILA, limite);
    }
}
