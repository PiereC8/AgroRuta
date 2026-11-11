package pe.agroruta.domain.port.out;

import java.time.Instant;
import java.util.Optional;

/** Las solicitudes para restablecer una contraseña olvidada, en la base del negocio indicado. */
public interface RecuperacionClavePort {

    record Cuenta(Long idUsuario, String nombreUsuario, String correo, boolean activo) {
    }

    Optional<Cuenta> buscarCuenta(String identificadorNegocio, String nombreUsuario);

    int solicitudesDesde(String identificadorNegocio, Long idUsuario, Instant desde);

    void guardarSolicitud(String identificadorNegocio, Long idUsuario, String hashToken, Instant creada,
                          Instant vence);

    /**
     * Marca como usado el token si está vigente y su usuario sigue activo; anula las demás solicitudes de ese usuario.
     * Devuelve el usuario solo si este llamado fue el que lo consumió (dos usos a la vez: solo uno gana).
     */
    Optional<Long> consumirToken(String identificadorNegocio, String hashToken, Instant ahora);

    /** Guarda la clave nueva y deja la cuenta sin intentos fallidos ni bloqueo. */
    void cambiarClave(String identificadorNegocio, Long idUsuario, String hashClave);
}
