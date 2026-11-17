package pe.agroruta.domain.port.in;

/** Segundo paso del inicio de sesión del Dueño: escribir el código de 6 dígitos que le llegó al correo. */
public interface VerificarCodigoUseCase {

    /** Si el código es correcto, entrega la sesión igual que un inicio de sesión normal. */
    IniciarSesionUseCase.Resultado verificar(String identificadorNegocio, String desafio, String codigo);

    /** Manda un código nuevo para el mismo inicio de sesión; devuelve el correo oculto al que se envió. */
    String reenviar(String identificadorNegocio, String desafio);
}
