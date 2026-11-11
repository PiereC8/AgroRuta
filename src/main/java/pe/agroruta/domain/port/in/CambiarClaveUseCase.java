package pe.agroruta.domain.port.in;

/** Cada usuario de un negocio cambia su propia contraseña sabiendo la actual. */
public interface CambiarClaveUseCase {

    void cambiar(String identificadorNegocio, String nombreUsuario, String claveActual, String claveNueva);
}
