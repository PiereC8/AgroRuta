package pe.agroruta.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Coordenada;
import pe.agroruta.domain.model.DestinoVenta;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.port.out.DestinoRepositoryPort;

@Component
public class DestinoJdbcAdapter implements DestinoRepositoryPort {

    private static final String COLUMNAS = "id_destino_venta, nombre_destino, ciudad_destino, es_local, destino_activo";

    private static final RowMapper<DestinoVenta> FILA = (rs, fila) -> new DestinoVenta(
            rs.getLong("id_destino_venta"),
            rs.getString("nombre_destino"),
            rs.getString("ciudad_destino"),
            rs.getBoolean("es_local"),
            rs.getBoolean("destino_activo"));

    private final JdbcTemplate jdbc;

    public DestinoJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public boolean existePorNombre(String nombreDestino) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM destino_venta WHERE nombre_destino = ?", Integer.class, nombreDestino);
        return total != null && total > 0;
    }

    @Override
    public DestinoVenta guardar(DestinoVenta destino) {
        var llave = new GeneratedKeyHolder();
        jdbc.update(conexion -> {
            PreparedStatement ps = conexion.prepareStatement(
                    "INSERT INTO destino_venta (nombre_destino, ciudad_destino, es_local, destino_activo) "
                            + "VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, destino.nombreDestino());
            ps.setString(2, destino.ciudadDestino());
            ps.setBoolean(3, destino.esLocal());
            ps.setBoolean(4, destino.destinoActivo());
            return ps;
        }, llave);
        return destino.conId(llave.getKey().longValue());
    }

    @Override
    public Optional<DestinoVenta> buscarPorId(Long idDestinoVenta) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM destino_venta WHERE id_destino_venta = ?", FILA,
                idDestinoVenta).stream().findFirst();
    }

    @Override
    public List<DestinoVenta> listarActivos() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM destino_venta WHERE destino_activo = TRUE "
                + "ORDER BY nombre_destino", FILA);
    }

    @Override
    public void guardarPrecioEsperado(Long idDestinoVenta, String productoLote, Dinero precioPorKg) {
        jdbc.update("INSERT INTO precio_destino (id_destino_venta, producto_lote, precio_esperado_kg) "
                        + "VALUES (?, ?, ?) AS nuevo "
                        + "ON DUPLICATE KEY UPDATE precio_esperado_kg = nuevo.precio_esperado_kg",
                idDestinoVenta, productoLote, precioPorKg.monto());
    }

    @Override
    public Map<Long, Dinero> preciosEsperadosPorKg(String productoLote) {
        Map<Long, Dinero> precios = new HashMap<>();
        jdbc.query("SELECT p.id_destino_venta, p.precio_esperado_kg FROM precio_destino p "
                        + "WHERE p.producto_lote = ?",
                rs -> {
                    precios.put(rs.getLong("id_destino_venta"), new Dinero(rs.getBigDecimal("precio_esperado_kg")));
                }, productoLote);
        return precios;
    }

    @Override
    public void guardarCoordenada(Long idDestinoVenta, Coordenada coordenada) {
        jdbc.update("INSERT INTO coordenada_destino (id_destino_venta, latitud, longitud) VALUES (?, ?, ?) AS nueva "
                        + "ON DUPLICATE KEY UPDATE latitud = nueva.latitud, longitud = nueva.longitud",
                idDestinoVenta, grados(coordenada.latitud()), grados(coordenada.longitud()));
    }

    @Override
    public Optional<Coordenada> buscarCoordenada(Long idDestinoVenta) {
        return jdbc.query("SELECT latitud, longitud FROM coordenada_destino WHERE id_destino_venta = ?",
                (rs, fila) -> new Coordenada(rs.getDouble("latitud"), rs.getDouble("longitud")),
                idDestinoVenta).stream().findFirst();
    }

    static BigDecimal grados(double valor) {
        return BigDecimal.valueOf(valor).setScale(6, java.math.RoundingMode.HALF_UP);
    }

    @Override
    public void guardarTarifaFlete(Long idDestinoVenta, Dinero costoFletePorKg) {
        BigDecimal monto = costoFletePorKg.monto();
        jdbc.update("INSERT INTO tarifa_flete_destino (id_destino_venta, costo_flete_kg) VALUES (?, ?) AS nuevo "
                        + "ON DUPLICATE KEY UPDATE costo_flete_kg = nuevo.costo_flete_kg",
                idDestinoVenta, monto);
    }
}
