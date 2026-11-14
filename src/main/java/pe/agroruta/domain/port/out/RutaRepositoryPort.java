package pe.agroruta.domain.port.out;

import java.util.Optional;

import pe.agroruta.domain.model.Trayecto;

public interface RutaRepositoryPort {

    /** Guarda la ruta del envío; si ya había una, la reemplaza. */
    void guardar(Long idEnvio, Trayecto trayecto);

    Optional<Trayecto> buscar(Long idEnvio);
}
