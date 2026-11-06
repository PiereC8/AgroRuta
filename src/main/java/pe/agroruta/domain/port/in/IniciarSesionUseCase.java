package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Rol;

public interface IniciarSesionUseCase {

    Resultado iniciar(Comando comando);

    record Comando(String identificadorNegocio, String nombreUsuario, String clave) {
    }

    record Resultado(String token, Rol rolUsuario, String identificadorNegocio) {
    }
}
