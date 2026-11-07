package pe.agroruta.infrastructure.adapter.out.persistence;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.MiembroEquipo;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.out.EquipoRepositoryPort;

@Component
public class EquipoJdbcAdapter implements EquipoRepositoryPort {

    private static final String COLUMNAS = "id_usuario, nombre_usuario, rol_usuario, correo_usuario, avisos_activos, "
            + "usuario_activo";

    private static final RowMapper<MiembroEquipo> FILA = (rs, fila) -> new MiembroEquipo(
            rs.getLong("id_usuario"),
            rs.getString("nombre_usuario"),
            Rol.valueOf(rs.getString("rol_usuario")),
            rs.getString("correo_usuario"),
            rs.getBoolean("avisos_activos"),
            rs.getBoolean("usuario_activo"));

    private final JdbcTemplate jdbc;

    public EquipoJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public List<MiembroEquipo> listar() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM usuario ORDER BY rol_usuario, nombre_usuario", FILA);
    }

    @Override
    public Optional<MiembroEquipo> buscar(Long idUsuario) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM usuario WHERE id_usuario = ?", FILA, idUsuario)
                .stream().findFirst();
    }

    @Override
    public boolean existePorNombre(String nombreUsuario) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE nombre_usuario = ?", Integer.class,
                nombreUsuario);
        return total != null && total > 0;
    }

    @Override
    public MiembroEquipo crearOperador(String nombreUsuario, String hashClave, String correo) {
        var llave = new GeneratedKeyHolder();
        jdbc.update(conexion -> {
            PreparedStatement ps = conexion.prepareStatement(
                    "INSERT INTO usuario (nombre_usuario, hash_clave, rol_usuario, correo_usuario) "
                            + "VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, nombreUsuario);
            ps.setString(2, hashClave);
            ps.setString(3, Rol.OPERADOR.name());
            ps.setString(4, correo);
            return ps;
        }, llave);
        return new MiembroEquipo(llave.getKey().longValue(), nombreUsuario, Rol.OPERADOR, correo, true, true);
    }

    @Override
    public void actualizarCorreo(Long idUsuario, String correo, boolean avisosActivos) {
        jdbc.update("UPDATE usuario SET correo_usuario = ?, avisos_activos = ? WHERE id_usuario = ?", correo,
                avisosActivos, idUsuario);
    }

    @Override
    public void actualizarActivo(Long idUsuario, boolean activo) {
        jdbc.update("UPDATE usuario SET usuario_activo = ? WHERE id_usuario = ?", activo, idUsuario);
    }

    @Override
    public List<MiembroEquipo> destinatarios(Set<Rol> roles) {
        if (roles.isEmpty()) {
            return List.of();
        }
        String marcas = roles.stream().map(r -> "?").collect(Collectors.joining(", "));
        return jdbc.query("SELECT " + COLUMNAS + " FROM usuario WHERE usuario_activo = TRUE AND avisos_activos = TRUE "
                        + "AND correo_usuario IS NOT NULL AND rol_usuario IN (" + marcas + ") ORDER BY id_usuario",
                FILA, roles.stream().map(Rol::name).toArray());
    }
}
