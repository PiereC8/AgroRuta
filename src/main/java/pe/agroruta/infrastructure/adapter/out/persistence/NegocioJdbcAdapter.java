package pe.agroruta.infrastructure.adapter.out.persistence;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.Plan;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;

@Component
public class NegocioJdbcAdapter implements NegocioRepositoryPort {

    private static final RowMapper<Negocio> FILA = (rs, fila) -> new Negocio(
            rs.getLong("id_negocio"),
            rs.getString("identificador_negocio"),
            rs.getString("nombre_negocio"),
            Plan.valueOf(rs.getString("plan_suscripcion")),
            rs.getBoolean("negocio_activo"));

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
    public Optional<Negocio> buscarActivoPorIdentificador(String identificadorNegocio) {
        return jdbc.query("SELECT id_negocio, identificador_negocio, nombre_negocio, plan_suscripcion, negocio_activo "
                        + "FROM negocio WHERE identificador_negocio = ? AND negocio_activo = TRUE",
                FILA, identificadorNegocio).stream().findFirst();
    }

    @Override
    public List<Negocio> listarActivos() {
        return jdbc.query("SELECT id_negocio, identificador_negocio, nombre_negocio, plan_suscripcion, negocio_activo "
                + "FROM negocio WHERE negocio_activo = TRUE ORDER BY id_negocio", FILA);
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
