package pe.agroruta.domain.port.in;

import java.math.BigDecimal;
import java.util.List;

import pe.agroruta.domain.model.Transportista;
import pe.agroruta.domain.model.Vehiculo;

public interface AdministrarTransporteUseCase {

    Transportista registrarTransportista(String nombreTransportista, String telefonoTransportista);

    List<Transportista> listarTransportistas();

    Vehiculo registrarVehiculo(Long idTransportista, String placaVehiculo, BigDecimal capacidadKg);

    List<Vehiculo> listarVehiculos();
}
