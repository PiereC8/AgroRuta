package pe.agroruta.infrastructure.config;

import java.util.Optional;

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
}
