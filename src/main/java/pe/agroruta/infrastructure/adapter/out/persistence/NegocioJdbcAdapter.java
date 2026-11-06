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
    public boolean existePorSlug(String slug) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM negocio WHERE slug = ?", Integer.class, slug);
        return total != null && total > 0;
    }

    @Override
    public Negocio guardar(Negocio negocio) {
        var llave = new GeneratedKeyHolder();
        jdbc.update(conexion -> {
            PreparedStatement ps = conexion.prepareStatement(
                    "INSERT INTO negocio (slug, nombre, plan, activo) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, negocio.slug());
            ps.setString(2, negocio.nombre());
            ps.setString(3, negocio.plan().name());
            ps.setBoolean(4, negocio.activo());
            return ps;
        }, llave);
        return negocio.conId(llave.getKey().longValue());
    }
}
