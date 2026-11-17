package pe.agroruta.domain.model;

import java.time.Instant;

/**
 * Un enlace guardado. Se autentica por el hash del token; el token además se guarda cifrado ({@code tokenCifrado},
 * puede venir en null en enlaces antiguos) para volver a mostrarlo con su QR al abrir «Enlaces» (RFF-09).
 */
public record EnlaceEnvio(String identificadorNegocio, Long idEnvio, TipoEnlace tipoEnlace, String hashToken,
                          Instant fechaExpiracion, String tokenCifrado) {

    public EnlaceEnvio(String identificadorNegocio, Long idEnvio, TipoEnlace tipoEnlace, String hashToken,
                       Instant fechaExpiracion) {
        this(identificadorNegocio, idEnvio, tipoEnlace, hashToken, fechaExpiracion, null);
    }

    public SesionEnlace sesion() {
        return new SesionEnlace(identificadorNegocio, idEnvio, tipoEnlace);
    }
}
