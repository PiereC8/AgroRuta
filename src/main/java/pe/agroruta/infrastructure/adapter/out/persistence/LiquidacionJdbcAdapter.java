package pe.agroruta.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.port.out.LiquidacionRepositoryPort;

@Component
public class LiquidacionJdbcAdapter implements LiquidacionRepositoryPort {

    private static final String COLUMNAS = "id_liquidacion, id_lote, id_pedido, id_envio, cantidad_despachada_kg, "
            + "cantidad_recibida_kg, merma_kg, rechazo_kg, precio_acordado_kg, costo_flete_kg, ingreso_real, "
            + "costo_flete_total, ganancia_real, ganancia_esperada, perdida_merma_rechazo, usuario_entrega, "
            + "fecha_entrega";

    private static final RowMapper<Liquidacion> FILA = (rs, fila) -> new Liquidacion(
            rs.getLong("id_liquidacion"),
            rs.getLong("id_lote"),
            rs.getLong("id_pedido"),
            rs.getLong("id_envio"),
            rs.getBigDecimal("cantidad_despachada_kg"),
            rs.getBigDecimal("cantidad_recibida_kg"),
            rs.getBigDecimal("merma_kg"),
            rs.getBigDecimal("rechazo_kg"),
            new Dinero(rs.getBigDecimal("precio_acordado_kg")),
            new Dinero(rs.getBigDecimal("costo_flete_kg")),
            new Dinero(rs.getBigDecimal("ingreso_real")),
            new Dinero(rs.getBigDecimal("costo_flete_total")),
            new Dinero(rs.getBigDecimal("ganancia_real")),
            new Dinero(rs.getBigDecimal("ganancia_esperada")),
            new Dinero(rs.getBigDecimal("perdida_merma_rechazo")),
            rs.getString("usuario_entrega"),
            Sql.instante(rs.getTimestamp("fecha_entrega")));

    private final JdbcTemplate jdbc;

    public LiquidacionJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public Liquidacion guardarNueva(Liquidacion l) {
        long id = Sql.insertar(jdbc, "INSERT INTO liquidacion (id_lote, id_pedido, id_envio, cantidad_despachada_kg, "
                        + "cantidad_recibida_kg, merma_kg, rechazo_kg, precio_acordado_kg, costo_flete_kg, "
                        + "ingreso_real, costo_flete_total, ganancia_real, ganancia_esperada, "
                        + "perdida_merma_rechazo, usuario_entrega, fecha_entrega) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                l.idLote(), l.idPedido(), l.idEnvio(), l.cantidadDespachadaKg(), l.cantidadRecibidaKg(),
                l.mermaKg(), l.rechazoKg(), l.precioAcordadoPorKg().monto(), l.costoFletePorKg().monto(),
                l.ingresoReal().monto(), l.costoFleteTotal().monto(), l.gananciaReal().monto(),
                l.gananciaEsperada().monto(), l.perdidaPorMermaYRechazo().monto(), l.usuarioEntrega(),
                Sql.marca(l.fechaEntrega()));
        return l.conId(id);
    }

    @Override
    public Optional<Liquidacion> buscarPorLote(Long idLote) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM liquidacion WHERE id_lote = ?", FILA, idLote)
                .stream().findFirst();
    }

    @Override
    public List<Liquidacion> listar(int pagina, int tamano) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM liquidacion ORDER BY id_liquidacion DESC LIMIT ? OFFSET ?",
                FILA, tamano, (long) pagina * tamano);
    }
}
