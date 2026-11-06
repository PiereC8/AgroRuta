package pe.agroruta.domain.port.out;

import java.util.List;
import java.util.Optional;

import pe.agroruta.domain.model.Envio;

public interface EnvioRepositoryPort {

    Envio guardarNuevo(Envio envio);

    /** Actualiza el estado y las fechas de un envío existente. */
    void actualizar(Envio envio);

    Optional<Envio> buscarPorId(Long idEnvio);

    List<Envio> listar(int pagina, int tamano);

    /** Un vehículo está ocupado mientras tenga un envío programado o en ruta. */
    boolean existeActivoDeVehiculo(Long idVehiculo);
}
