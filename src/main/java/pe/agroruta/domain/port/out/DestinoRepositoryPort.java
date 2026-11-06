package pe.agroruta.domain.port.out;

import java.util.List;
import java.util.Optional;

import pe.agroruta.domain.model.DestinoVenta;

public interface DestinoRepositoryPort {

    boolean existePorNombre(String nombreDestino);

    DestinoVenta guardar(DestinoVenta destino);

    Optional<DestinoVenta> buscarPorId(Long idDestinoVenta);

    List<DestinoVenta> listarActivos();
}
