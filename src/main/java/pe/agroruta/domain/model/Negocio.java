package pe.agroruta.domain.model;

import java.util.regex.Pattern;

public record Negocio(Long idNegocio, String identificadorNegocio, String nombreNegocio, Plan planSuscripcion,
                      boolean negocioActivo) {

    private static final Pattern FORMATO_IDENTIFICADOR = Pattern.compile("^[a-z][a-z0-9_]{2,30}$");

    public Negocio {
        if (identificadorNegocio == null || !FORMATO_IDENTIFICADOR.matcher(identificadorNegocio).matches()) {
            throw new DominioException(
                    "El identificador debe tener de 3 a 31 caracteres: minúsculas, números o guion bajo, y empezar con letra");
        }
        if (nombreNegocio == null || nombreNegocio.isBlank() || nombreNegocio.length() > 120) {
            throw new DominioException("El nombre del negocio es obligatorio y tiene hasta 120 caracteres");
        }
        if (planSuscripcion == null) {
            throw new DominioException("El plan de suscripción es obligatorio");
        }
    }

    public static Negocio nuevo(String identificadorNegocio, String nombreNegocio, Plan planSuscripcion) {
        return new Negocio(null, identificadorNegocio,
                nombreNegocio == null ? null : nombreNegocio.strip(), planSuscripcion, true);
    }

    public Negocio conId(Long nuevoIdNegocio) {
        return new Negocio(nuevoIdNegocio, identificadorNegocio, nombreNegocio, planSuscripcion, negocioActivo);
    }
}
