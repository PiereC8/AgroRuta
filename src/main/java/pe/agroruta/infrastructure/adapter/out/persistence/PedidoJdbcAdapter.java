package pe.agroruta.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EstadoPedido;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;

@Component
public class PedidoJdbcAdapter implements PedidoRepositoryPort {

    private static final String COLUMNAS = "id_pedido, id_comprador, id_lote, id_destino_venta, cantidad_kg, "
            + "precio_acordado_kg, estado_pedido, usuario_registro, fecha_pedido";

    private static final RowMapper<Pedido> FILA = (rs, fila) -> new Pedido(
            rs.getLong("id_pedido"),
            rs.getLong("id_comprador"),
            rs.getLong("id_lote"),
            rs.getLong("id_destino_venta"),
            rs.getBigDecimal("cantidad_kg"),
            new Dinero(rs.getBigDecimal("precio_acordado_kg")),
            EstadoPedido.valueOf(rs.getString("estado_pedido")),
            rs.getString("usuario_registro"),
            Sql.instante(rs.getTimestamp("fecha_pedido")));

    private final JdbcTemplate jdbc;

    public PedidoJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public Pedido guardarNuevo(Pedido pedido) {
        long id = Sql.insertar(jdbc, "INSERT INTO pedido (id_comprador, id_lote, id_destino_venta, cantidad_kg, "
                        + "precio_acordado_kg, estado_pedido, usuario_registro, fecha_pedido) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                pedido.idComprador(), pedido.idLote(), pedido.idDestinoVenta(), pedido.cantidadKg(),
                pedido.precioAcordadoPorKg().monto(), pedido.estadoPedido().name(), pedido.usuarioRegistro(),
                Sql.marca(pedido.fechaPedido()));
        return pedido.conId(id);
    }

    @Override
    public Optional<Pedido> buscarPorId(Long idPedido) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM pedido WHERE id_pedido = ?", FILA, idPedido)
                .stream().findFirst();
    }

    @Override
    public boolean existeParaLote(Long idLote) {
        // Un pedido cancelado ya no ocupa el lote.
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM pedido WHERE id_lote = ? "
                + "AND estado_pedido <> 'CANCELADO'", Integer.class, idLote);
        return total != null && total > 0;
    }

    @Override
    public List<Pedido> listar(int pagina, int tamano) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM pedido ORDER BY id_pedido DESC LIMIT ? OFFSET ?", FILA,
                tamano, (long) pagina * tamano);
    }

    @Override
    public void actualizarEstado(Long idPedido, EstadoPedido nuevoEstado) {
        jdbc.update("UPDATE pedido SET estado_pedido = ? WHERE id_pedido = ?", nuevoEstado.name(), idPedido);
    }
}
