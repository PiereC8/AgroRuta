package pe.agroruta.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Comprador;
import pe.agroruta.domain.model.TipoComprador;
import pe.agroruta.domain.port.out.CompradorRepositoryPort;

@Component
public class CompradorJdbcAdapter implements CompradorRepositoryPort {

    private static final String COLUMNAS = "id_comprador, nombre_comprador, tipo_comprador, documento_comprador, "
            + "telefono_comprador, ciudad_comprador, comprador_activo";

    private static final RowMapper<Comprador> FILA = (rs, fila) -> new Comprador(
            rs.getLong("id_comprador"),
            rs.getString("nombre_comprador"),
            TipoComprador.valueOf(rs.getString("tipo_comprador")),
            rs.getString("documento_comprador"),
            rs.getString("telefono_comprador"),
            rs.getString("ciudad_comprador"),
            rs.getBoolean("comprador_activo"));

    private final JdbcTemplate jdbc;

    public CompradorJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public boolean existePorDocumento(String documentoComprador) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM comprador WHERE documento_comprador = ?", Integer.class, documentoComprador);
        return total != null && total > 0;
    }

    @Override
    public Comprador guardar(Comprador comprador) {
        long id = Sql.insertar(jdbc, "INSERT INTO comprador (nombre_comprador, tipo_comprador, documento_comprador, "
                        + "telefono_comprador, ciudad_comprador, comprador_activo) VALUES (?, ?, ?, ?, ?, ?)",
                comprador.nombreComprador(), comprador.tipoComprador().name(), comprador.documentoComprador(),
                comprador.telefonoComprador(), comprador.ciudadComprador(), comprador.compradorActivo());
        return comprador.conId(id);
    }

    @Override
    public Optional<Comprador> buscarPorId(Long idComprador) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM comprador WHERE id_comprador = ?", FILA, idComprador)
                .stream().findFirst();
    }

    @Override
    public List<Comprador> listarActivos() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM comprador WHERE comprador_activo = TRUE "
                + "ORDER BY nombre_comprador", FILA);
    }
}
