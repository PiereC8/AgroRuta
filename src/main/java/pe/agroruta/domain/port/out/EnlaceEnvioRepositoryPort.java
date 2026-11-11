package pe.agroruta.domain.port.out;

import java.time.Instant;
import java.util.Optional;

import pe.agroruta.domain.model.EnlaceEnvio;
import pe.agroruta.domain.model.TipoEnlace;

public interface EnlaceEnvioRepositoryPort {

    void guardar(EnlaceEnvio enlace);

    void desactivarDeEnvio(String identificadorNegocio, Long idEnvio, TipoEnlace tipo);

    Optional<EnlaceEnvio> buscarVigentePorHash(String hashToken, Instant ahora);
}
