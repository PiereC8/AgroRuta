package pe.agroruta.domain.port.in;

import java.time.Instant;

public interface GenerarEnlacesEnvioUseCase {

    EnlacesGenerados generar(String identificadorNegocio, Long idEnvio);

    record EnlacesGenerados(String tokenConductor, String tokenComprador, Instant fechaExpiracion) {
    }
}
