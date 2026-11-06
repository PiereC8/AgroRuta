package pe.agroruta.infrastructure.adapter.out.persistence;

import java.sql.PreparedStatement;
import java.sql.Statement;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;

@Component
public class NegocioJdbcAdapter implements NegocioRepositoryPort {

    private final JdbcTemplate jdbc;

    public NegocioJdbcAdapter(@Qualifier("plataformaJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existePorIdentificador(String identificadorNegocio) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM negocio WHERE identificador_negocio = ?", Integer.class, identificadorNegocio);
        return total != null && total > 0;
    }

    @Override
    public Negocio guardar(Negocio negocio) {
        var llave = new GeneratedKeyHolder();
        jdbc.update(conexion -> {
            PreparedStatement ps = conexion.prepareStatement(
                    "INSERT INTO negocio (identificador_negocio, nombre_negocio, plan_suscripcion, negocio_activo) "
                            + "VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, negocio.identificadorNegocio());
            ps.setString(2, negocio.nombreNegocio());
            ps.setString(3, negocio.planSuscripcion().name());
            ps.setBoolean(4, negocio.negocioActivo());
            return ps;
        }, llave);
        return negocio.conId(llave.getKey().longValue());
    }
}
