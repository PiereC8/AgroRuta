package pe.agroruta.domain.port.out;

import java.time.Instant;
import java.util.Optional;

/** RFF-01: la sesión abierta de cada cuenta del negocio (solo su huella y su último uso). */
public interface SesionUsuarioPort {

    /** Último uso de la sesión guardada de la cuenta, si hay una. */
    Optional<Instant> ultimoUso(String identificadorNegocio, String nombreUsuario);

    /**
     * Abre la sesión solo si la cuenta no tiene otra usada después de {@code libreAntesDe}. Devuelve false si había
     * una sesión abierta (si dos dispositivos entran a la vez, solo uno gana).
     */
    boolean abrirSiLibre(String identificadorNegocio, String nombreUsuario, String hashSesion, Instant ahora,
                         Instant libreAntesDe);

    /**
     * Si la sesión del token sigue siendo la de la cuenta, el usuario está activo y no pasó el tiempo de
     * inactividad, anota el uso y devuelve true.
     */
    boolean usar(String identificadorNegocio, String nombreUsuario, String hashSesion, Instant ahora,
                 Instant libreAntesDe);

    /** Cierra la sesión si es la del token (no toca una sesión más nueva de otro dispositivo). */
    void cerrar(String identificadorNegocio, String nombreUsuario, String hashSesion);
}
