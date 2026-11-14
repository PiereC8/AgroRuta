package pe.agroruta.domain.port.in;

import java.util.List;
import java.util.Optional;

import pe.agroruta.domain.model.Coordenada;
import pe.agroruta.domain.model.DestinoVenta;
import pe.agroruta.domain.model.Dinero;

public interface AdministrarDestinosUseCase {

    DestinoVenta registrarDestino(String nombreDestino, String ciudadDestino, boolean esLocal);

    List<DestinoVenta> listarDestinos();

    void fijarPrecioEsperado(Long idDestinoVenta, String productoLote, Dinero precioPorKg);

    void fijarTarifaFlete(Long idDestinoVenta, Dinero costoFletePorKg);

    void fijarCoordenadas(Long idDestinoVenta, double latitud, double longitud);

    /** La ubicación guardada del destino, si ya se marcó en el mapa. */
    Optional<Coordenada> coordenadasDe(Long idDestinoVenta);
}
