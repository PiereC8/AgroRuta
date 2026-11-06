package pe.agroruta.domain.port.out;

import java.util.Optional;

import pe.agroruta.domain.model.Coordenada;

public interface UbicacionDestinoPort {

    void guardarCoordenada(Long idDestinoVenta, Coordenada coordenada);

    Optional<Coordenada> buscarCoordenada(Long idDestinoVenta);
}
