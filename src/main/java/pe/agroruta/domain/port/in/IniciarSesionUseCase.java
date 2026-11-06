package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Rol;

public interface IniciarSesionUseCase {

    /** Si {@code identificadorNegocio} es nulo, se autentica un administrador de la plataforma. */
    Resultado iniciar(Comando comando);

    record Comando(String identificadorNegocio, String nombreUsuario, String clave) {
    }

    record Resultado(String token, Rol rolUsuario, String identificadorNegocio) {
    }
}
