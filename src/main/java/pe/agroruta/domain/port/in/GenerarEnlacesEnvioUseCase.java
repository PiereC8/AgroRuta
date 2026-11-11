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
}
