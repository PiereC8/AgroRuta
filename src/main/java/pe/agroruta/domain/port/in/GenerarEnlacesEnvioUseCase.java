package pe.agroruta.domain.port.in;

import java.time.Instant;

import pe.agroruta.domain.model.TipoEnlace;

public interface GenerarEnlacesEnvioUseCase {

    /** Genera los dos enlaces del envío; los anteriores dejan de valer. */
    default EnlacesGenerados generar(String identificadorNegocio, Long idEnvio) {
        return generar(identificadorNegocio, idEnvio, null);
    }

    /**
     * Genera solo el enlace del tipo indicado (o los dos si es null). Solo deja de valer el enlace anterior de ese
     * mismo tipo: renovar el del comprador no corta al conductor que está compartiendo su ubicación.
     * El token del enlace que no se generó viene en null.
     */
    EnlacesGenerados generar(String identificadorNegocio, Long idEnvio, TipoEnlace soloTipo);

    record EnlacesGenerados(String tokenConductor, String tokenComprador, Instant fechaExpiracion) {
    }

    /**
     * RFF-09: los enlaces vigentes del envío, para mostrarlos con su QR al abrir «Enlaces» sin cortar a nadie.
     * Cada token viene en null si no hay uno vigente que se pueda mostrar (por ejemplo, uno creado antes de que se
     * guardaran cifrados): entonces la pantalla ofrece generarlo.
     */
    default EnlacesVigentes vigentes(String identificadorNegocio, Long idEnvio) {
        return new EnlacesVigentes(null, null, null, null);
    }

    record EnlacesVigentes(String tokenConductor, Instant venceConductor, String tokenComprador,
                           Instant venceComprador) {
    }
}
