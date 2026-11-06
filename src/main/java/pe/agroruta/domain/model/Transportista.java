package pe.agroruta.domain.model;

import java.util.regex.Pattern;

public record Transportista(Long idTransportista, String nombreTransportista, String telefonoTransportista,
                            boolean transportistaActivo) {

    private static final Pattern FORMATO_TELEFONO = Pattern.compile("^[0-9+() -]{6,20}$");

    public Transportista {
        if (nombreTransportista == null || nombreTransportista.strip().length() < 2
                || nombreTransportista.strip().length() > 120) {
            throw new DominioException("El nombre del transportista es obligatorio y tiene de 2 a 120 caracteres");
        }
        nombreTransportista = nombreTransportista.strip();
        telefonoTransportista = telefonoTransportista == null || telefonoTransportista.isBlank() ? null
                : telefonoTransportista.strip();
        if (telefonoTransportista != null && !FORMATO_TELEFONO.matcher(telefonoTransportista).matches()) {
            throw new DominioException("El teléfono solo admite números, espacios, +, paréntesis y guion (6 a 20)");
        }
    }

    public static Transportista nuevo(String nombreTransportista, String telefonoTransportista) {
        return new Transportista(null, nombreTransportista, telefonoTransportista, true);
    }

    public Transportista conId(Long nuevoIdTransportista) {
        return new Transportista(nuevoIdTransportista, nombreTransportista, telefonoTransportista,
                transportistaActivo);
    }
}
