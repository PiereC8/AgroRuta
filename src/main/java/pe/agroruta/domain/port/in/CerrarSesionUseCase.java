package pe.agroruta.domain.port.in;

/** RFF-01 (sesión única): «Cerrar sesión» libera la cuenta para entrar desde otro dispositivo. */
public interface CerrarSesionUseCase {

    void cerrar(String identificadorNegocio, String nombreUsuario, String sesion);
}
