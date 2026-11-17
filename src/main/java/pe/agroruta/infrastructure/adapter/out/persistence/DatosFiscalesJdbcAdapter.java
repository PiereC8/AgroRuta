package pe.agroruta.infrastructure.adapter.out.persistence;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.DatosFiscales;
import pe.agroruta.domain.port.out.DatosFiscalesPort;

@Component
public class DatosFiscalesJdbcAdapter implements DatosFiscalesPort {

    private final JdbcTemplate jdbc;

    public DatosFiscalesJdbcAdapter(@Qualifier("plataformaJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DatosFiscales> buscar(String identificadorNegocio) {
        return jdbc.query("SELECT ruc_emisor, razon_social, domicilio_fiscal FROM negocio "
                        + "WHERE identificador_negocio = ? AND ruc_emisor IS NOT NULL AND razon_social IS NOT NULL "
                        + "AND domicilio_fiscal IS NOT NULL",
                (rs, fila) -> new DatosFiscales(rs.getString("ruc_emisor"), rs.getString("razon_social"),
                        rs.getString("domicilio_fiscal")), identificadorNegocio).stream().findFirst();
    }

    @Override
    public boolean guardar(String identificadorNegocio, DatosFiscales datos) {
        return jdbc.update("UPDATE negocio SET ruc_emisor = ?, razon_social = ?, domicilio_fiscal = ? "
                        + "WHERE identificador_negocio = ?", datos.ruc(), datos.razonSocial(), datos.domicilioFiscal(),
                identificadorNegocio) > 0;
    }
}
