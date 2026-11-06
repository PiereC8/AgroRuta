package pe.agroruta.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.CobrosPendientes;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EnvioEnCurso;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.port.out.PanelRepositoryPort;

/** Sumas y listas de solo lectura para el panel de inicio. */
@Component
public class PanelJdbcAdapter implements PanelRepositoryPort {

    /** MySQL no admite fechas anteriores a 1970 en columnas TIMESTAMP; un inicio muy antiguo se acota. */
    private static final Instant PRIMER_DIA_ADMITIDO = Instant.parse("2000-01-01T00:00:00Z");

    private final JdbcTemplate jdbc;

    public PanelJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public Dinero gananciaEntre(Instant desde, Instant hasta) {
        return new Dinero(suma("ganancia_real", desde, hasta));
    }

    @Override
    public BigDecimal mermaKgEntre(Instant desde, Instant hasta) {
        return suma("merma_kg", desde, hasta);
    }

    @Override
    public Dinero perdidaEntre(Instant desde, Instant hasta) {
        return new Dinero(suma("perdida_merma_rechazo", desde, hasta));
    }

    @Override
    public List<EnvioEnCurso> enviosEnCurso(int limite) {
        return jdbc.query("SELECT e.id_envio, e.estado_envio, d.nombre_destino, c.nombre_comprador, "
                        + "p.cantidad_kg, e.fecha_programada "
                        + "FROM envio e JOIN pedido p ON p.id_pedido = e.id_pedido "
                        + "JOIN destino_venta d ON d.id_destino_venta = p.id_destino_venta "
                        + "JOIN comprador c ON c.id_comprador = p.id_comprador "
                        + "WHERE e.estado_envio IN ('PROGRAMADO', 'EN_RUTA') "
                        + "ORDER BY e.fecha_programada ASC, e.id_envio ASC LIMIT ?",
                (rs, fila) -> new EnvioEnCurso(rs.getLong("id_envio"),
                        EstadoEnvio.valueOf(rs.getString("estado_envio")), rs.getString("nombre_destino"),
                        rs.getString("nombre_comprador"), rs.getBigDecimal("cantidad_kg"),
                        Sql.instante(rs.getTimestamp("fecha_programada"))),
                limite);
    }

    @Override
    public CobrosPendientes cobrosPendientes() {
        return jdbc.queryForObject("SELECT COALESCE(SUM(l.ingreso_real), 0), COUNT(*) FROM liquidacion l "
                        + "WHERE NOT EXISTS (SELECT 1 FROM pago p WHERE p.id_liquidacion = l.id_liquidacion "
                        + "AND p.estado_pago = 'APROBADO')",
                (rs, fila) -> new CobrosPendientes(new Dinero(rs.getBigDecimal(1)), rs.getInt(2)));
    }

    private BigDecimal suma(String columna, Instant desde, Instant hasta) {
        Instant inicio = desde.isBefore(PRIMER_DIA_ADMITIDO) ? PRIMER_DIA_ADMITIDO : desde;
        return jdbc.queryForObject("SELECT COALESCE(SUM(" + columna + "), 0) FROM liquidacion "
                        + "WHERE fecha_entrega >= ? AND fecha_entrega < ?",
                BigDecimal.class, Sql.marca(inicio), Sql.marca(hasta));
    }
}
