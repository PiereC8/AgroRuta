package pe.agroruta.infrastructure.adapter.out.persistence;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

final class Sql {

    private Sql() {
    }

    static long insertar(JdbcTemplate jdbc, String sql, Object... parametros) {
        var llave = new GeneratedKeyHolder();
        jdbc.update(conexion -> {
            PreparedStatement ps = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < parametros.length; i++) {
                ps.setObject(i + 1, parametros[i]);
            }
            return ps;
        }, llave);
        return llave.getKey().longValue();
    }

    static Timestamp marca(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }

    static Instant instante(Timestamp marca) {
        return marca == null ? null : marca.toInstant();
    }
}
