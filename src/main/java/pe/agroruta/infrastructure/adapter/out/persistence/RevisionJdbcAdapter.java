package pe.agroruta.infrastructure.adapter.out.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.HechoDelNegocio.AlertaDeEnvio;
import pe.agroruta.domain.model.HechoDelNegocio.CobroVencido;
import pe.agroruta.domain.model.HechoDelNegocio.ComprobanteAtascado;
import pe.agroruta.domain.model.HechoDelNegocio.ComprobanteRechazado;
import pe.agroruta.domain.model.HechoDelNegocio.EntregaRegistrada;
import pe.agroruta.domain.model.HechoDelNegocio.PagoRecibido;
import pe.agroruta.domain.model.TipoAlerta;
import pe.agroruta.domain.port.out.RevisionRepositoryPort;

@Component
public class RevisionJdbcAdapter implements RevisionRepositoryPort {

    private static final int LIMITE = 500;

    private static final String ENTREGA = "FROM liquidacion l "
            + "JOIN pedido p ON p.id_pedido = l.id_pedido "
            + "JOIN lote lo ON lo.id_lote = l.id_lote "
            + "JOIN destino_venta d ON d.id_destino_venta = p.id_destino_venta "
            + "JOIN comprador c ON c.id_comprador = p.id_comprador ";

    private final JdbcTemplate jdbc;

    public RevisionJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public List<EntregaRegistrada> entregasDesde(Instant desde) {
        return jdbc.query("SELECT l.id_liquidacion, c.nombre_comprador, lo.producto_lote, d.nombre_destino, "
                + "l.cantidad_despachada_kg, l.merma_kg, l.rechazo_kg, l.ganancia_real, l.perdida_merma_rechazo "
                + ENTREGA + "WHERE l.fecha_entrega >= ? ORDER BY l.id_liquidacion LIMIT ?",
                (rs, fila) -> new EntregaRegistrada(rs.getLong("id_liquidacion"), rs.getString("nombre_comprador"),
                        rs.getString("producto_lote"), rs.getString("nombre_destino"),
                        rs.getBigDecimal("cantidad_despachada_kg"), rs.getBigDecimal("merma_kg"),
                        rs.getBigDecimal("rechazo_kg"), new Dinero(rs.getBigDecimal("ganancia_real")),
                        new Dinero(rs.getBigDecimal("perdida_merma_rechazo"))),
                Sql.marca(desde), LIMITE);
    }

    @Override
    public List<PagoRecibido> pagosAprobadosDesde(Instant desde) {
        return jdbc.query("SELECT pg.id_pago, pg.id_pedido, pg.monto, c.nombre_comprador FROM pago pg "
                + "JOIN pedido p ON p.id_pedido = pg.id_pedido JOIN comprador c ON c.id_comprador = p.id_comprador "
                + "WHERE pg.estado_pago = 'APROBADO' AND pg.fecha_actualizacion >= ? ORDER BY pg.id_pago LIMIT ?",
                (rs, fila) -> new PagoRecibido(rs.getLong("id_pago"), rs.getString("nombre_comprador"),
                        new Dinero(rs.getBigDecimal("monto")), rs.getLong("id_pedido")),
                Sql.marca(desde), LIMITE);
    }

    @Override
    public List<AlertaDeEnvio> alertasDesde(Instant desde) {
        return jdbc.query("SELECT a.id_alerta, a.id_envio, a.tipo_alerta, a.descripcion_alerta, d.nombre_destino "
                + "FROM alerta_envio a JOIN envio e ON e.id_envio = a.id_envio "
                + "JOIN pedido p ON p.id_pedido = e.id_pedido "
                + "JOIN destino_venta d ON d.id_destino_venta = p.id_destino_venta "
                + "WHERE a.fecha_alerta >= ? ORDER BY a.id_alerta LIMIT ?",
                (rs, fila) -> new AlertaDeEnvio(rs.getLong("id_alerta"), rs.getLong("id_envio"),
                        TipoAlerta.valueOf(rs.getString("tipo_alerta")), rs.getString("nombre_destino"),
                        rs.getString("descripcion_alerta")),
                Sql.marca(desde), LIMITE);
    }

    @Override
    public List<ComprobanteRechazado> comprobantesRechazadosDesde(Instant desde) {
        return jdbc.query("SELECT id_comprobante, serie, numero, mensaje_sunat FROM comprobante "
                + "WHERE estado_comprobante = 'RECHAZADO' AND fecha_creacion >= ? ORDER BY id_comprobante LIMIT ?",
                (rs, fila) -> new ComprobanteRechazado(rs.getLong("id_comprobante"),
                        numero(rs.getString("serie"), rs.getInt("numero")),
                        rs.getString("mensaje_sunat") == null ? "sin detalle" : rs.getString("mensaje_sunat")),
                Sql.marca(desde), LIMITE);
    }

    @Override
    public List<ComprobanteAtascado> comprobantesAtascados(Instant creadosAntesDe, Instant ahora) {
        return jdbc.query("SELECT id_comprobante, serie, numero, fecha_creacion FROM comprobante "
                + "WHERE estado_comprobante = 'PENDIENTE' AND fecha_creacion < ? ORDER BY id_comprobante LIMIT ?",
                (rs, fila) -> new ComprobanteAtascado(rs.getLong("id_comprobante"),
                        numero(rs.getString("serie"), rs.getInt("numero")),
                        Duration.between(Sql.instante(rs.getTimestamp("fecha_creacion")), ahora).toHours()),
                Sql.marca(creadosAntesDe), LIMITE);
    }

    @Override
    public List<CobroVencido> cobrosVencidos(Instant entregadosAntesDe, Instant ahora) {
        return jdbc.query("SELECT l.id_liquidacion, l.ingreso_real, l.fecha_entrega, c.nombre_comprador "
                + "FROM liquidacion l JOIN pedido p ON p.id_pedido = l.id_pedido "
                + "JOIN comprador c ON c.id_comprador = p.id_comprador "
                + "WHERE l.fecha_entrega < ? AND l.ingreso_real > 0 AND NOT EXISTS "
                + "(SELECT 1 FROM pago pg WHERE pg.id_liquidacion = l.id_liquidacion AND pg.estado_pago = 'APROBADO') "
                + "ORDER BY l.id_liquidacion LIMIT ?",
                (rs, fila) -> new CobroVencido(rs.getLong("id_liquidacion"), rs.getString("nombre_comprador"),
                        new Dinero(rs.getBigDecimal("ingreso_real")),
                        (int) Duration.between(Sql.instante(rs.getTimestamp("fecha_entrega")), ahora).toDays()),
                Sql.marca(entregadosAntesDe), LIMITE);
    }

    private static String numero(String serie, int numero) {
        return String.format("%s-%08d", serie, numero);
    }
}
