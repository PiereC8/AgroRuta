package pe.agroruta.domain.port.out;

import java.util.List;
import java.util.Optional;

import pe.agroruta.domain.model.Transportista;
import pe.agroruta.domain.model.Vehiculo;

public interface TransporteRepositoryPort {

    Transportista guardarTransportista(Transportista transportista);

    Optional<Transportista> buscarTransportista(Long idTransportista);

    List<Transportista> listarTransportistas();

    boolean existeVehiculoPorPlaca(String placaVehiculo);

    Vehiculo guardarVehiculo(Vehiculo vehiculo);

    Optional<Vehiculo> buscarVehiculo(Long idVehiculo);

    List<Vehiculo> listarVehiculos();
}
