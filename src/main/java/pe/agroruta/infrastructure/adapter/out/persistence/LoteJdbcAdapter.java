package pe.agroruta.infrastructure.adapter.out.persistence;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.EstadoLote;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.TipoEventoLote;
import pe.agroruta.domain.port.out.LoteRepositoryPort;

@Component
public class LoteJdbcAdapter implements LoteRepositoryPort {

    private static final String COLUMNAS_LOTE = "id_lote, producto_lote, cantidad_kg, fecha_cosecha, chacra_origen, "
            + "estado_lote, usuario_registro, fecha_registro";

    private static final RowMapper<Lote> FILA_LOTE = (rs, fila) -> new Lote(
            rs.getLong("id_lote"),
            rs.getString("producto_lote"),
            rs.getBigDecimal("cantidad_kg"),
            rs.getDate("fecha_cosecha").toLocalDate(),
            rs.getString("chacra_origen"),
            EstadoLote.valueOf(rs.getString("estado_lote")),
            rs.getString("usuario_registro"),
            rs.getTimestamp("fecha_registro").toInstant());

    private static final RowMapper<EventoLote> FILA_EVENTO = (rs, fila) -> new EventoLote(
            TipoEventoLote.valueOf(rs.getString("tipo_evento")),
            rs.getString("descripcion_evento"),
            rs.getString("usuario_evento"),
            rs.getTimestamp("fecha_evento").toInstant());

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public LoteJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource,
                           @Qualifier("transaccionesNegocios") PlatformTransactionManager transacciones) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
        this.transaccion = new TransactionTemplate(transacciones);
    }

    @Override
    public Lote guardarNuevo(Lote lote, EventoLote primerEvento) {
        return transaccion.execute(estado -> {
            var llave = new GeneratedKeyHolder();
            jdbc.update(conexion -> {
                PreparedStatement ps = conexion.prepareStatement(
                        "INSERT INTO lote (producto_lote, cantidad_kg, fecha_cosecha, chacra_origen, estado_lote, "
                                + "usuario_registro, fecha_registro) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, lote.productoLote());
                ps.setBigDecimal(2, lote.cantidadKg());
                ps.setDate(3, Date.valueOf(lote.fechaCosecha()));
                ps.setString(4, lote.chacraOrigen());
                ps.setString(5, lote.estadoLote().name());
                ps.setString(6, lote.usuarioRegistro());
                ps.setTimestamp(7, Timestamp.from(lote.fechaRegistro()));
                return ps;
            }, llave);
            Lote guardado = lote.conId(llave.getKey().longValue());
            insertarEvento(guardado.idLote(), primerEvento);
            return guardado;
        });
    }

    private void insertarEvento(Long idLote, EventoLote evento) {
        jdbc.update("INSERT INTO evento_lote (id_lote, tipo_evento, descripcion_evento, usuario_evento, fecha_evento) "
                        + "VALUES (?, ?, ?, ?, ?)",
                idLote, evento.tipoEvento().name(), evento.descripcionEvento(), evento.usuarioEvento(),
                Timestamp.from(evento.fechaEvento()));
    }

    @Override
    public void actualizarEstado(Long idLote, EstadoLote nuevoEstado, EventoLote evento) {
        transaccion.executeWithoutResult(estado -> {
            jdbc.update("UPDATE lote SET estado_lote = ? WHERE id_lote = ?", nuevoEstado.name(), idLote);
            insertarEvento(idLote, evento);
        });
    }

    @Override
    public Optional<Lote> buscarPorId(Long idLote) {
        return jdbc.query("SELECT " + COLUMNAS_LOTE + " FROM lote WHERE id_lote = ?", FILA_LOTE, idLote)
                .stream().findFirst();
    }

    @Override
    public List<Lote> listar(int pagina, int tamano) {
        return jdbc.query("SELECT " + COLUMNAS_LOTE + " FROM lote ORDER BY fecha_cosecha DESC, id_lote DESC LIMIT ? OFFSET ?",
                FILA_LOTE, tamano, (long) pagina * tamano);
    }

    @Override
    public List<EventoLote> historial(Long idLote) {
        return jdbc.query("SELECT tipo_evento, descripcion_evento, usuario_evento, fecha_evento FROM evento_lote "
                + "WHERE id_lote = ? ORDER BY fecha_evento, id_evento_lote", FILA_EVENTO, idLote);
    }
}
