package pe.agroruta.infrastructure.adapter.out.persistence;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.function.Function;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.Usuario;
import pe.agroruta.domain.port.out.UsuarioRepositoryPort;
import pe.agroruta.infrastructure.config.TenantContext;

@Component
public class UsuarioJdbcAdapter implements UsuarioRepositoryPort {

    private static final String COLUMNAS = "id_usuario, nombre_usuario, hash_clave, rol_usuario, "
            + "intentos_fallidos_sesion, fecha_bloqueo_hasta, usuario_activo";

    private static final RowMapper<Usuario> FILA = (rs, fila) -> {
        Timestamp bloqueo = rs.getTimestamp("fecha_bloqueo_hasta");
        return new Usuario(
                rs.getLong("id_usuario"),
                rs.getString("nombre_usuario"),
                rs.getString("hash_clave"),
                Rol.valueOf(rs.getString("rol_usuario")),
                rs.getInt("intentos_fallidos_sesion"),
                bloqueo == null ? null : bloqueo.toInstant(),
                rs.getBoolean("usuario_activo"));
    };

    private record Destino(JdbcTemplate jdbc, String tabla) {
    }

    private final Destino plataforma;
    private final Destino negocio;

    public UsuarioJdbcAdapter(@Qualifier("plataformaJdbc") JdbcTemplate plataformaJdbc,
                              @Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.plataforma = new Destino(plataformaJdbc, "administrador_plataforma");
        this.negocio = new Destino(new JdbcTemplate(negociosDataSource), "usuario");
    }

    @Override
    public Optional<Usuario> buscarPorNombre(String identificadorNegocio, String nombreUsuario) {
        return en(identificadorNegocio, d -> d.jdbc().query(
                "SELECT " + COLUMNAS + " FROM " + d.tabla() + " WHERE nombre_usuario = ?",
                FILA, nombreUsuario).stream().findFirst());
    }

    @Override
    public boolean existePorNombre(String identificadorNegocio, String nombreUsuario) {
        return en(identificadorNegocio, d -> {
            Integer total = d.jdbc().queryForObject(
                    "SELECT COUNT(*) FROM " + d.tabla() + " WHERE nombre_usuario = ?", Integer.class, nombreUsuario);
            return total != null && total > 0;
        });
    }

    @Override
    public Usuario guardar(String identificadorNegocio, Usuario usuario) {
        return en(identificadorNegocio, d -> usuario.idUsuario() == null ? insertar(d, usuario) : actualizar(d, usuario));
    }

    private Usuario insertar(Destino d, Usuario usuario) {
        var llave = new GeneratedKeyHolder();
        d.jdbc().update(conexion -> {
            PreparedStatement ps = conexion.prepareStatement(
                    "INSERT INTO " + d.tabla() + " (nombre_usuario, hash_clave, rol_usuario, "
                            + "intentos_fallidos_sesion, fecha_bloqueo_hasta, usuario_activo) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, usuario.nombreUsuario());
            ps.setString(2, usuario.hashClave());
            ps.setString(3, usuario.rolUsuario().name());
            ps.setInt(4, usuario.intentosFallidosSesion());
            ps.setTimestamp(5, usuario.fechaBloqueoHasta() == null ? null : Timestamp.from(usuario.fechaBloqueoHasta()));
            ps.setBoolean(6, usuario.usuarioActivo());
            return ps;
        }, llave);
        return usuario.conId(llave.getKey().longValue());
    }

    private Usuario actualizar(Destino d, Usuario usuario) {
        d.jdbc().update("UPDATE " + d.tabla() + " SET intentos_fallidos_sesion = ?, fecha_bloqueo_hasta = ?, "
                        + "usuario_activo = ? WHERE id_usuario = ?",
                usuario.intentosFallidosSesion(),
                usuario.fechaBloqueoHasta() == null ? null : Timestamp.from(usuario.fechaBloqueoHasta()),
                usuario.usuarioActivo(),
                usuario.idUsuario());
        return usuario;
    }

    private <T> T en(String identificadorNegocio, Function<Destino, T> accion) {
        if (identificadorNegocio == null) {
            return accion.apply(plataforma);
        }
        return TenantContext.ejecutarEn(identificadorNegocio, () -> accion.apply(negocio));
    }
}
