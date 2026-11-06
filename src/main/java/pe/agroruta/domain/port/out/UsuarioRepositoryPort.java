package pe.agroruta.domain.port.out;

import java.util.Optional;

import pe.agroruta.domain.model.Usuario;

/** Con identificador de negocio nulo, trabaja sobre los administradores de la plataforma. */
public interface UsuarioRepositoryPort {

    Optional<Usuario> buscarPorNombre(String identificadorNegocio, String nombreUsuario);

    boolean existePorNombre(String identificadorNegocio, String nombreUsuario);

    Usuario guardar(String identificadorNegocio, Usuario usuario);
}
