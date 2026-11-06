package pe.agroruta.domain.model;

import java.util.regex.Pattern;

public record Negocio(Long id, String slug, String nombre, Plan plan, boolean activo) {

    private static final Pattern FORMATO_SLUG = Pattern.compile("^[a-z][a-z0-9_]{2,30}$");

    public Negocio {
        if (slug == null || !FORMATO_SLUG.matcher(slug).matches()) {
            throw new DominioException(
                    "El identificador debe tener de 3 a 31 caracteres: minúsculas, números o guion bajo, y empezar con letra");
        }
        if (nombre == null || nombre.isBlank() || nombre.length() > 120) {
            throw new DominioException("El nombre es obligatorio y tiene hasta 120 caracteres");
        }
        if (plan == null) {
            throw new DominioException("El plan es obligatorio");
        }
    }

    public static Negocio nuevo(String slug, String nombre, Plan plan) {
        return new Negocio(null, slug, nombre == null ? null : nombre.strip(), plan, true);
    }

    public Negocio conId(Long nuevoId) {
        return new Negocio(nuevoId, slug, nombre, plan, activo);
    }
}
