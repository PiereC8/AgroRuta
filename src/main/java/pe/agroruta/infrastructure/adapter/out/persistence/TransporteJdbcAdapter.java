package pe.agroruta.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Transportista;
import pe.agroruta.domain.model.Vehiculo;
import pe.agroruta.domain.port.out.TransporteRepositoryPort;

@Component
public class TransporteJdbcAdapter implements TransporteRepositoryPort {

    private static final String COLUMNAS_TRANSPORTISTA =
            "id_transportista, nombre_transportista, telefono_transportista, transportista_activo";
    private static final String COLUMNAS_VEHICULO =
            "id_vehiculo, id_transportista, placa_vehiculo, capacidad_kg, vehiculo_activo";

    private static final RowMapper<Transportista> FILA_TRANSPORTISTA = (rs, fila) -> new Transportista(
            rs.getLong("id_transportista"),
            rs.getString("nombre_transportista"),
            rs.getString("telefono_transportista"),
            rs.getBoolean("transportista_activo"));

    private static final RowMapper<Vehiculo> FILA_VEHICULO = (rs, fila) -> new Vehiculo(
            rs.getLong("id_vehiculo"),
            rs.getLong("id_transportista"),
            rs.getString("placa_vehiculo"),
            rs.getBigDecimal("capacidad_kg"),
            rs.getBoolean("vehiculo_activo"));

    private final JdbcTemplate jdbc;

    public TransporteJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public Transportista guardarTransportista(Transportista transportista) {
        long id = Sql.insertar(jdbc, "INSERT INTO transportista (nombre_transportista, telefono_transportista, "
                        + "transportista_activo) VALUES (?, ?, ?)",
                transportista.nombreTransportista(), transportista.telefonoTransportista(),
                transportista.transportistaActivo());
        return transportista.conId(id);
    }

    @Override
    public Optional<Transportista> buscarTransportista(Long idTransportista) {
        return jdbc.query("SELECT " + COLUMNAS_TRANSPORTISTA + " FROM transportista WHERE id_transportista = ?",
                FILA_TRANSPORTISTA, idTransportista).stream().findFirst();
    }

    @Override
    public List<Transportista> listarTransportistas() {
        return jdbc.query("SELECT " + COLUMNAS_TRANSPORTISTA + " FROM transportista WHERE transportista_activo = TRUE "
                + "ORDER BY nombre_transportista", FILA_TRANSPORTISTA);
    }

    @Override
    public boolean existeVehiculoPorPlaca(String placaVehiculo) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM vehiculo WHERE placa_vehiculo = ?", Integer.class,
                placaVehiculo);
        return total != null && total > 0;
    }

    @Override
    public Vehiculo guardarVehiculo(Vehiculo vehiculo) {
        long id = Sql.insertar(jdbc, "INSERT INTO vehiculo (id_transportista, placa_vehiculo, capacidad_kg, "
                        + "vehiculo_activo) VALUES (?, ?, ?, ?)",
                vehiculo.idTransportista(), vehiculo.placaVehiculo(), vehiculo.capacidadKg(),
                vehiculo.vehiculoActivo());
        return vehiculo.conId(id);
    }

    @Override
    public Optional<Vehiculo> buscarVehiculo(Long idVehiculo) {
        return jdbc.query("SELECT " + COLUMNAS_VEHICULO + " FROM vehiculo WHERE id_vehiculo = ?", FILA_VEHICULO,
                idVehiculo).stream().findFirst();
    }

    @Override
    public List<Vehiculo> listarVehiculos() {
        return jdbc.query("SELECT " + COLUMNAS_VEHICULO + " FROM vehiculo WHERE vehiculo_activo = TRUE "
                + "ORDER BY placa_vehiculo", FILA_VEHICULO);
    }
}
