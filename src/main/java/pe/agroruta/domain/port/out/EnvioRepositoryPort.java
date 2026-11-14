package pe.agroruta.domain.port.out;

import java.util.List;
import java.util.Optional;

import pe.agroruta.domain.model.Envio;

public interface EnvioRepositoryPort {

    Envio guardarNuevo(Envio envio);

    void actualizar(Envio envio);

    Optional<Envio> buscarPorId(Long idEnvio);

    List<Envio> listar(int pagina, int tamano);

    /** ¿Algún envío de ese lote va en ruta? Con eso se calcula el estado del lote. */
    boolean existeEnRutaDeLote(Long idLote);

    /** Un vehículo está ocupado mientras tenga un envío programado o en ruta. */
    boolean existeActivoDeVehiculo(Long idVehiculo);
}
