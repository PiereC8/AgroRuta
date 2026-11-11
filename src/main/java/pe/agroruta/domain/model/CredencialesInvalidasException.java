package pe.agroruta.domain.model;

/** Mensaje único para todo fallo de inicio de sesión: no revela si el usuario existe ni si está bloqueado. */
public class CredencialesInvalidasException extends DominioException {

    /** Avisa del bloqueo a todos por igual, sin decir si esta cuenta existe o está bloqueada. */
    public static final String MENSAJE = "Credenciales inválidas. Después de 5 intentos fallidos la cuenta se bloquea "
            + "15 minutos.";

    public CredencialesInvalidasException() {
        super(MENSAJE);
    }
}
