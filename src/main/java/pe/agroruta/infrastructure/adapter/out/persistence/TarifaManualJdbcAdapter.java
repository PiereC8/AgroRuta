package pe.agroruta.infrastructure.adapter.out.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.port.out.TarifaPort;

/** Tarifas de flete cargadas a mano por el negocio. Para usar una API externa, se crea otro adaptador de TarifaPort. */
@Component
public class TarifaManualJdbcAdapter implements TarifaPort {

    private final NamedParameterJdbcTemplate jdbc;

    public TarifaManualJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(negociosDataSource);
    }

    @Override
    public Map<Long, Dinero> costosFletePorKg(Collection<Long> idsDestinoVenta) {
        Map<Long, Dinero> tarifas = new HashMap<>();
        if (idsDestinoVenta.isEmpty()) {
            return tarifas;
        }
        jdbc.query("SELECT id_destino_venta, costo_flete_kg FROM tarifa_flete_destino "
                        + "WHERE id_destino_venta IN (:ids)",
                new MapSqlParameterSource("ids", idsDestinoVenta),
                rs -> {
                    tarifas.put(rs.getLong("id_destino_venta"), new Dinero(rs.getBigDecimal("costo_flete_kg")));
                });
        return tarifas;
    }
}
