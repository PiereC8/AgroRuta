package pe.agroruta.infrastructure.config;

import java.util.Optional;
import java.util.function.Supplier;

public final class TenantContext {

    private static final ThreadLocal<String> NEGOCIO_ACTUAL = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void fijar(String identificadorNegocio) {
        NEGOCIO_ACTUAL.set(identificadorNegocio);
    }

    public static Optional<String> actual() {
        return Optional.ofNullable(NEGOCIO_ACTUAL.get());
    }

    public static void limpiar() {
        NEGOCIO_ACTUAL.remove();
    }

    /** Ejecuta una acción dentro de la base de un negocio y deja el contexto como estaba. */
    public static <T> T ejecutarEn(String identificadorNegocio, Supplier<T> accion) {
        String previo = NEGOCIO_ACTUAL.get();
        NEGOCIO_ACTUAL.set(identificadorNegocio);
        try {
            return accion.get();
        } finally {
            if (previo == null) {
                NEGOCIO_ACTUAL.remove();
            } else {
                NEGOCIO_ACTUAL.set(previo);
            }
        }
    }
}
