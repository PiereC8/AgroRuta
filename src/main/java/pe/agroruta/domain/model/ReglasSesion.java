package pe.agroruta.domain.model;

import java.time.Duration;
import java.time.Instant;

/**
 * RFF-01: una sola sesión abierta por cuenta del negocio (Dueño u Operador).
 * Cómo: mientras la sesión tenga actividad en los últimos 5 minutos, no se puede entrar con la misma cuenta desde
 * otro dispositivo. La página abierta avisa cada 2 minutos que sigue activa; «Cerrar sesión» libera la cuenta.
 */
public final class ReglasSesion {

    public static final Duration INACTIVIDAD = Duration.ofMinutes(5);

    public static final String SESION_ABIERTA = "Esta cuenta ya tiene una sesión abierta en otro dispositivo. "
            + "Ciérrala allí con «Cerrar sesión» o espera 5 minutos sin actividad para entrar aquí.";

    private ReglasSesion() {
    }

    /** El administrador de la plataforma no pertenece a un negocio y no tiene esta regla. */
    public static boolean aplicaA(String identificadorNegocio) {
        return identificadorNegocio != null;
    }

    /** Una sesión cuenta como abierta si se usó hace menos de 5 minutos. */
    public static boolean estaAbierta(Instant ultimoUso, Instant ahora) {
        return ultimoUso != null && ultimoUso.isAfter(ahora.minus(INACTIVIDAD));
    }

    /** Antes de este momento, una sesión ya está libre. */
    public static Instant libreAntesDe(Instant ahora) {
        return ahora.minus(INACTIVIDAD);
    }
}
