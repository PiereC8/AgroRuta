package pe.agroruta.domain.port.in;

/** «Olvidé mi contraseña»: un enlace de un solo uso por correo para poner una clave nueva. */
public interface RecuperarClaveUseCase {

    /**
     * Si la cuenta existe, está activa y tiene correo, le manda el enlace. No avisa de nada en ningún caso, para no
     * revelar qué cuentas existen.
     */
    void solicitar(String identificadorNegocio, String nombreUsuario, String urlBase);

    /** Usa el enlace: cambia la clave y quita el bloqueo. Falla si el enlace no vale o ya se usó. */
    void restablecer(String identificadorNegocio, String token, String claveNueva);
}
