package pe.agroruta.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.ResumenCobro;
import pe.agroruta.domain.port.out.CobroPedidoPort;

@Component
public class CobroPedidoJdbcAdapter implements CobroPedidoPort {

    private final JdbcTemplate jdbc;

    public CobroPedidoJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public Map<Long, ResumenCobro> resumenDe(Collection<Long> idPedidos) {
        Map<Long, ResumenCobro> resumen = new HashMap<>();
        if (idPedidos.isEmpty()) {
            return resumen;
        }
        String marcas = String.join(", ", Collections.nCopies(idPedidos.size(), "?"));
        jdbc.query("SELECT l.id_pedido, SUM(l.ingreso_real) AS a_cobrar, "
                        + "(SELECT COALESCE(SUM(pg.monto), 0) FROM pago pg "
                        + " WHERE pg.id_pedido = l.id_pedido AND pg.estado_pago = 'APROBADO') AS cobrado "
                        + "FROM liquidacion l WHERE l.id_pedido IN (" + marcas + ") GROUP BY l.id_pedido",
                rs -> {
                    BigDecimal cobrado = rs.getBigDecimal("cobrado");
                    resumen.put(rs.getLong("id_pedido"), new ResumenCobro(new Dinero(rs.getBigDecimal("a_cobrar")),
                            new Dinero(cobrado == null ? BigDecimal.ZERO : cobrado)));
                }, idPedidos.toArray());
        return resumen;
    }
}
