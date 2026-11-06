package pe.agroruta.domain.port.in;

import java.time.Instant;

public interface GenerarEnlacesEnvioUseCase {

    /** Crea un enlace para el conductor y otro para el comprador. Los anteriores dejan de valer. */
    EnlacesGenerados generar(String identificadorNegocio, Long idEnvio);

    record EnlacesGenerados(String tokenConductor, String tokenComprador, Instant fechaExpiracion) {
    }
}
