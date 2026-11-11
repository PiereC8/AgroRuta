package pe.agroruta.domain.port.out;

import java.util.Optional;

import pe.agroruta.domain.model.Usuario;

public interface UsuarioRepositoryPort {

    Optional<Usuario> buscarPorNombre(String identificadorNegocio, String nombreUsuario);

    boolean existePorNombre(String identificadorNegocio, String nombreUsuario);

    Usuario guardar(String identificadorNegocio, Usuario usuario);

    void cambiarClave(String identificadorNegocio, Long idUsuario, String hashClave);
}
