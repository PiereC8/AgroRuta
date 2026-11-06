package pe.agroruta.application.service;

import java.math.BigDecimal;
import java.util.List;

import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.Transportista;
import pe.agroruta.domain.model.Vehiculo;
import pe.agroruta.domain.port.in.AdministrarTransporteUseCase;
import pe.agroruta.domain.port.out.TransporteRepositoryPort;

public class AdministrarTransporteService implements AdministrarTransporteUseCase {

    private final TransporteRepositoryPort transporte;

    public AdministrarTransporteService(TransporteRepositoryPort transporte) {
        this.transporte = transporte;
    }

    @Override
    public Transportista registrarTransportista(String nombreTransportista, String telefonoTransportista) {
        return transporte.guardarTransportista(Transportista.nuevo(nombreTransportista, telefonoTransportista));
    }

    @Override
    public List<Transportista> listarTransportistas() {
        return transporte.listarTransportistas();
    }

    @Override
    public Vehiculo registrarVehiculo(Long idTransportista, String placaVehiculo, BigDecimal capacidadKg) {
        Vehiculo vehiculo = Vehiculo.nuevo(idTransportista, placaVehiculo, capacidadKg);
        transporte.buscarTransportista(idTransportista)
                .filter(Transportista::transportistaActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el transportista " + idTransportista));
        if (transporte.existeVehiculoPorPlaca(vehiculo.placaVehiculo())) {
            throw new ConflictoDominioException("Ya existe un vehículo con la placa " + vehiculo.placaVehiculo());
        }
        return transporte.guardarVehiculo(vehiculo);
    }

    @Override
    public List<Vehiculo> listarVehiculos() {
        return transporte.listarVehiculos();
    }
}
