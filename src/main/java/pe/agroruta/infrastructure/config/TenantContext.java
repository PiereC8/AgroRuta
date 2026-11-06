package pe.agroruta.infrastructure.config;

import java.util.Optional;

public final class TenantContext {

    private static final ThreadLocal<String> ACTUAL = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void fijar(String slug) {
        ACTUAL.set(slug);
    }

    public static Optional<String> actual() {
        return Optional.ofNullable(ACTUAL.get());
    }

    public static void limpiar() {
        ACTUAL.remove();
    }
}
