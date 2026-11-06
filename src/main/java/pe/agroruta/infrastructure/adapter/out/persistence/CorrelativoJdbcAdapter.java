package pe.agroruta.infrastructure.adapter.out.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.port.out.CorrelativoPort;

/** Un solo contador por serie en la base central, porque todos los negocios comparten la cuenta del proveedor. */
@Component
public class CorrelativoJdbcAdapter implements CorrelativoPort {

    private final JdbcTemplate jdbc;

    public CorrelativoJdbcAdapter(@Qualifier("plataformaJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int siguiente(String serie) {
        // El insert y la lectura del número deben ocurrir en la misma conexión.
        return jdbc.execute((java.sql.Connection conexion) -> {
            try (PreparedStatement ps = conexion.prepareStatement(
                    "INSERT INTO correlativo_comprobante (serie, ultimo_numero) VALUES (?, LAST_INSERT_ID(1)) "
                            + "ON DUPLICATE KEY UPDATE ultimo_numero = LAST_INSERT_ID(ultimo_numero + 1)")) {
                ps.setString(1, serie);
                ps.executeUpdate();
            }
            try (Statement consulta = conexion.createStatement();
                 ResultSet resultado = consulta.executeQuery("SELECT LAST_INSERT_ID()")) {
                resultado.next();
                return resultado.getInt(1);
            }
        });
    }
}
