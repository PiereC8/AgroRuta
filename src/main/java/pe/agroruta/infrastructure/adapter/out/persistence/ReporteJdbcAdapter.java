package pe.agroruta.infrastructure.adapter.out.persistence;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.FilaEntrega;
import pe.agroruta.domain.model.FiltroReporte;
import pe.agroruta.domain.port.out.ReporteRepositoryPort;

@Component
public class ReporteJdbcAdapter implements ReporteRepositoryPort {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static final String BASE = "FROM liquidacion l "
            + "JOIN pedido p ON p.id_pedido = l.id_pedido "
            + "JOIN lote lo ON lo.id_lote = l.id_lote "
            + "JOIN destino_venta d ON d.id_destino_venta = p.id_destino_venta "
            + "JOIN comprador c ON c.id_comprador = p.id_comprador ";

    private final JdbcTemplate jdbc;

    public ReporteJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public List<FilaEntrega> entregas(FiltroReporte filtro, int limite) {
        StringBuilder sql = new StringBuilder("SELECT l.id_liquidacion, l.id_pedido, l.fecha_entrega, "
                + "c.nombre_comprador, lo.producto_lote, d.nombre_destino, l.cantidad_despachada_kg, "
                + "l.cantidad_recibida_kg, l.merma_kg, l.rechazo_kg, l.ingreso_real, l.costo_flete_total, "
                + "l.ganancia_real, l.ganancia_esperada, l.perdida_merma_rechazo, "
                + "EXISTS (SELECT 1 FROM pago pg WHERE pg.id_liquidacion = l.id_liquidacion "
                + "AND pg.estado_pago = 'APROBADO') AS cobrada ")
                .append(BASE)
                .append("WHERE l.fecha_entrega >= ? AND l.fecha_entrega < ? ");
        List<Object> parametros = new ArrayList<>();
        parametros.add(Sql.marca(filtro.desde().atStartOfDay(LIMA).toInstant()));
        parametros.add(Sql.marca(filtro.hasta().plusDays(1).atStartOfDay(LIMA).toInstant()));
        if (filtro.producto() != null) {
            sql.append("AND lo.producto_lote = ? ");
            parametros.add(filtro.producto());
        }
        if (filtro.idDestino() != null) {
            sql.append("AND p.id_destino_venta = ? ");
            parametros.add(filtro.idDestino());
        }
        sql.append("ORDER BY l.fecha_entrega DESC, l.id_liquidacion DESC LIMIT ?");
        parametros.add(limite);
        return jdbc.query(sql.toString(), (rs, fila) -> new FilaEntrega(rs.getLong("id_liquidacion"),
                rs.getLong("id_pedido"), Sql.instante(rs.getTimestamp("fecha_entrega")),
                rs.getString("nombre_comprador"), rs.getString("producto_lote"), rs.getString("nombre_destino"),
                rs.getBigDecimal("cantidad_despachada_kg"), rs.getBigDecimal("cantidad_recibida_kg"),
                rs.getBigDecimal("merma_kg"), rs.getBigDecimal("rechazo_kg"),
                new Dinero(rs.getBigDecimal("ingreso_real")), new Dinero(rs.getBigDecimal("costo_flete_total")),
                new Dinero(rs.getBigDecimal("ganancia_real")), new Dinero(rs.getBigDecimal("ganancia_esperada")),
                new Dinero(rs.getBigDecimal("perdida_merma_rechazo")), rs.getBoolean("cobrada")),
                parametros.toArray());
    }

    @Override
    public List<String> productosConEntregas() {
        return jdbc.queryForList("SELECT DISTINCT lo.producto_lote FROM liquidacion l "
                + "JOIN lote lo ON lo.id_lote = l.id_lote ORDER BY lo.producto_lote", String.class);
    }
}
