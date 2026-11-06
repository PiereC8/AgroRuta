package pe.agroruta.domain.model;

import java.time.Instant;

/** Un enlace guardado: solo se conoce el hash del token, nunca el token. */
public record EnlaceEnvio(String identificadorNegocio, Long idEnvio, TipoEnlace tipoEnlace, String hashToken,
                          Instant fechaExpiracion) {

    public SesionEnlace sesion() {
        return new SesionEnlace(identificadorNegocio, idEnvio, tipoEnlace);
    }
}
