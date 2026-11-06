package pe.agroruta.domain.port.out;

import java.time.Instant;
import java.util.Optional;

import pe.agroruta.domain.model.EnlaceEnvio;

/** Guarda los enlaces en la base central, para poder encontrar el negocio a partir de un token. */
public interface EnlaceEnvioRepositoryPort {

    void guardar(EnlaceEnvio enlace);

    void desactivarDeEnvio(String identificadorNegocio, Long idEnvio);

    Optional<EnlaceEnvio> buscarVigentePorHash(String hashToken, Instant ahora);
}
