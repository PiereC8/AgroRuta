package pe.agroruta.domain.model;

/** Mensaje único para todo fallo de inicio de sesión: no revela si el usuario existe ni si está bloqueado. */
public class CredencialesInvalidasException extends DominioException {

    public CredencialesInvalidasException() {
        super("Credenciales inválidas");
    }
}
