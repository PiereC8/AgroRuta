package pe.agroruta.application.service;

import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.PoliticaClave;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.Usuario;
import pe.agroruta.domain.port.in.CambiarClaveUseCase;
import pe.agroruta.domain.port.out.HashClavePort;
import pe.agroruta.domain.port.out.UsuarioRepositoryPort;

public class CambiarClaveService implements CambiarClaveUseCase {

    private final UsuarioRepositoryPort usuarios;
    private final HashClavePort hashes;

    public CambiarClaveService(UsuarioRepositoryPort usuarios, HashClavePort hashes) {
        this.usuarios = usuarios;
        this.hashes = hashes;
    }

    @Override
    public void cambiar(String identificadorNegocio, String nombreUsuario, String claveActual, String claveNueva) {
        if (identificadorNegocio == null) {
            throw new ConflictoDominioException("La contraseña del administrador de la plataforma se cambia en la "
                    + "configuración del servidor");
        }
        Usuario usuario = usuarios.buscarPorNombre(identificadorNegocio, nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario " + nombreUsuario));
        if (claveActual == null || !hashes.coincide(claveActual, usuario.hashClave())) {
            throw new DominioException("La contraseña actual no es correcta");
        }
        PoliticaClave.validar(claveNueva);
        if (claveNueva.equals(claveActual)) {
            throw new DominioException("La contraseña nueva debe ser distinta de la actual");
        }
        usuarios.cambiarClave(identificadorNegocio, usuario.idUsuario(), hashes.cifrar(claveNueva));
    }
}
