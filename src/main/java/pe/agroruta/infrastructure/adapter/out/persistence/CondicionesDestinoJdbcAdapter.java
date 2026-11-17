package pe.agroruta.infrastructure.adapter.out.persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.CondicionesDestino;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.port.out.CondicionesDestinoPort;

/** Lee directo de la base (sin el caché del cotizador) para mostrar lo recién guardado. */
@Component
public class CondicionesDestinoJdbcAdapter implements CondicionesDestinoPort {

    private final JdbcTemplate jdbc;

    public CondicionesDestinoJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public Map<Long, CondicionesDestino> vigentes() {
        Map<Long, Dinero> fletes = new HashMap<>();
        jdbc.query("SELECT id_destino_venta, costo_flete_kg FROM tarifa_flete_destino",
                rs -> {
                    fletes.put(rs.getLong("id_destino_venta"), new Dinero(rs.getBigDecimal("costo_flete_kg")));
                });
        Map<Long, Map<String, Dinero>> precios = new HashMap<>();
        jdbc.query("SELECT id_destino_venta, producto_lote, precio_esperado_kg FROM precio_destino",
                rs -> {
                    precios.computeIfAbsent(rs.getLong("id_destino_venta"), k -> new TreeMap<>())
                            .put(rs.getString("producto_lote"), new Dinero(rs.getBigDecimal("precio_esperado_kg")));
                });
        Map<Long, CondicionesDestino> condiciones = new HashMap<>();
        fletes.forEach((id, flete) -> condiciones.put(id, new CondicionesDestino(flete, Map.of())));
        precios.forEach((id, lista) -> condiciones.put(id,
                new CondicionesDestino(fletes.get(id), lista)));
        return condiciones;
    }
}
