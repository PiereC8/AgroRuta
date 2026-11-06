package pe.agroruta.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.AlertaEnvio;
import pe.agroruta.domain.model.Coordenada;
import pe.agroruta.domain.model.Posicion;
import pe.agroruta.domain.model.TipoAlerta;
import pe.agroruta.domain.port.out.SeguimientoRepositoryPort;

@Component
public class SeguimientoJdbcAdapter implements SeguimientoRepositoryPort {

    private static final String COLUMNAS_POSICION =
            "id_posicion, id_envio, latitud, longitud, precision_metros, fecha_posicion, fecha_recepcion";

    private static final RowMapper<Posicion> FILA_POSICION = (rs, fila) -> {
        BigDecimal precision = rs.getBigDecimal("precision_metros");
        return new Posicion(
                rs.getLong("id_posicion"),
                rs.getLong("id_envio"),
                new Coordenada(rs.getDouble("latitud"), rs.getDouble("longitud")),
                precision == null ? null : precision.doubleValue(),
                Sql.instante(rs.getTimestamp("fecha_posicion")),
                Sql.instante(rs.getTimestamp("fecha_recepcion")));
    };

    private static final RowMapper<AlertaEnvio> FILA_ALERTA = (rs, fila) -> new AlertaEnvio(
            rs.getLong("id_alerta"),
            rs.getLong("id_envio"),
            TipoAlerta.valueOf(rs.getString("tipo_alerta")),
            rs.getString("descripcion_alerta"),
            Sql.instante(rs.getTimestamp("fecha_alerta")));

    private final JdbcTemplate jdbc;

    public SeguimientoJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public Optional<Posicion> guardarPosicion(Posicion p) {
        try {
            long id = Sql.insertar(jdbc, "INSERT INTO posicion_envio (id_envio, latitud, longitud, precision_metros, "
                            + "fecha_posicion, fecha_recepcion) VALUES (?, ?, ?, ?, ?, ?)",
                    p.idEnvio(), DestinoJdbcAdapter.grados(p.coordenada().latitud()),
                    DestinoJdbcAdapter.grados(p.coordenada().longitud()),
                    p.precisionMetros() == null ? null
                            : BigDecimal.valueOf(p.precisionMetros()).setScale(1, RoundingMode.HALF_UP),
                    Sql.marca(p.fechaPosicion()), Sql.marca(p.fechaRecepcion()));
            return Optional.of(p.conId(id));
        } catch (DuplicateKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Posicion> posicionesDesde(Long idEnvio, Instant desde) {
        return jdbc.query("SELECT " + COLUMNAS_POSICION + " FROM posicion_envio WHERE id_envio = ? "
                + "AND fecha_posicion >= ? ORDER BY fecha_posicion", FILA_POSICION, idEnvio, Sql.marca(desde));
    }

    @Override
    public List<Posicion> ultimasPosiciones(Long idEnvio, int cantidad) {
        return jdbc.query("SELECT * FROM (SELECT " + COLUMNAS_POSICION + " FROM posicion_envio WHERE id_envio = ? "
                + "ORDER BY fecha_posicion DESC LIMIT ?) ultimas ORDER BY fecha_posicion", FILA_POSICION, idEnvio,
                cantidad);
    }

    @Override
    public AlertaEnvio guardarAlerta(AlertaEnvio alerta) {
        long id = Sql.insertar(jdbc, "INSERT INTO alerta_envio (id_envio, tipo_alerta, descripcion_alerta, "
                        + "fecha_alerta) VALUES (?, ?, ?, ?)",
                alerta.idEnvio(), alerta.tipoAlerta().name(), alerta.descripcionAlerta(),
                Sql.marca(alerta.fechaAlerta()));
        return alerta.conId(id);
    }

    @Override
    public boolean existeAlertaDesde(Long idEnvio, TipoAlerta tipoAlerta, Instant desde) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM alerta_envio WHERE id_envio = ? "
                + "AND tipo_alerta = ? AND fecha_alerta >= ?", Integer.class, idEnvio, tipoAlerta.name(),
                Sql.marca(desde));
        return total != null && total > 0;
    }

    @Override
    public List<AlertaEnvio> alertas(Long idEnvio) {
        return jdbc.query("SELECT id_alerta, id_envio, tipo_alerta, descripcion_alerta, fecha_alerta "
                + "FROM alerta_envio WHERE id_envio = ? ORDER BY fecha_alerta, id_alerta", FILA_ALERTA, idEnvio);
    }

    @Override
    public void registrarConsentimiento(Long idEnvio, Instant fecha) {
        jdbc.update("INSERT IGNORE INTO seguimiento_envio (id_envio, fecha_consentimiento) VALUES (?, ?)",
                idEnvio, Sql.marca(fecha));
    }

    @Override
    public boolean tieneConsentimiento(Long idEnvio) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM seguimiento_envio WHERE id_envio = ?",
                Integer.class, idEnvio);
        return total != null && total > 0;
    }
}
