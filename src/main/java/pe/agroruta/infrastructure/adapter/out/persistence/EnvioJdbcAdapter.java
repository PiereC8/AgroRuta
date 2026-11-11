package pe.agroruta.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;

@Component
public class EnvioJdbcAdapter implements EnvioRepositoryPort {

    private static final String COLUMNAS = "id_envio, id_pedido, id_lote, id_vehiculo, id_transportista, "
            + "estado_envio, fecha_programada, fecha_salida, fecha_entrega, usuario_despacho";

    private static final RowMapper<Envio> FILA = (rs, fila) -> new Envio(
            rs.getLong("id_envio"),
            rs.getLong("id_pedido"),
            rs.getLong("id_lote"),
            rs.getLong("id_vehiculo"),
            rs.getLong("id_transportista"),
            EstadoEnvio.valueOf(rs.getString("estado_envio")),
            Sql.instante(rs.getTimestamp("fecha_programada")),
            Sql.instante(rs.getTimestamp("fecha_salida")),
            Sql.instante(rs.getTimestamp("fecha_entrega")),
            rs.getString("usuario_despacho"));

    private final JdbcTemplate jdbc;

    public EnvioJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public Envio guardarNuevo(Envio envio) {
        try {
            long id = Sql.insertar(jdbc, "INSERT INTO envio (id_pedido, id_lote, id_vehiculo, id_transportista, "
                            + "estado_envio, fecha_programada, fecha_salida, fecha_entrega, usuario_despacho) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    envio.idPedido(), envio.idLote(), envio.idVehiculo(), envio.idTransportista(),
                    envio.estadoEnvio().name(), Sql.marca(envio.fechaProgramada()), Sql.marca(envio.fechaSalida()),
                    Sql.marca(envio.fechaEntrega()), envio.usuarioDespacho());
            return envio.conId(id);
        } catch (DuplicateKeyException e) {
            // Dos despachos al mismo tiempo: la base no deja el mismo vehículo ni el mismo pedido en dos envíos activos.
            String detalle = String.valueOf(e.getMostSpecificCause().getMessage());
            throw new ConflictoDominioException(detalle.contains("vehiculo")
                    ? "Ese vehículo ya tiene un envío en curso" : "Ese pedido ya tiene un envío en curso");
        }
    }

    @Override
    public void actualizar(Envio envio) {
        jdbc.update("UPDATE envio SET estado_envio = ?, fecha_salida = ?, fecha_entrega = ? WHERE id_envio = ?",
                envio.estadoEnvio().name(), Sql.marca(envio.fechaSalida()), Sql.marca(envio.fechaEntrega()),
                envio.idEnvio());
    }

    @Override
    public Optional<Envio> buscarPorId(Long idEnvio) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM envio WHERE id_envio = ?", FILA, idEnvio)
                .stream().findFirst();
    }

    @Override
    public List<Envio> listar(int pagina, int tamano) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM envio ORDER BY id_envio DESC LIMIT ? OFFSET ?", FILA,
                tamano, (long) pagina * tamano);
    }

    @Override
    public boolean existeActivoDeVehiculo(Long idVehiculo) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM envio WHERE id_vehiculo = ? "
                + "AND estado_envio IN ('PROGRAMADO', 'EN_RUTA')", Integer.class, idVehiculo);
        return total != null && total > 0;
    }
}
