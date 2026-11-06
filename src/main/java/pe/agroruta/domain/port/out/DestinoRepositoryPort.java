package pe.agroruta.domain.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.agroruta.domain.model.Coordenada;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DestinoVenta;

public interface DestinoRepositoryPort {

    boolean existePorNombre(String nombreDestino);

    DestinoVenta guardar(DestinoVenta destino);

    Optional<DestinoVenta> buscarPorId(Long idDestinoVenta);

    List<DestinoVenta> listarActivos();

    void guardarPrecioEsperado(Long idDestinoVenta, String productoLote, Dinero precioPorKg);

    Map<Long, Dinero> preciosEsperadosPorKg(String productoLote);

    void guardarCoordenada(Long idDestinoVenta, Coordenada coordenada);

    Optional<Coordenada> buscarCoordenada(Long idDestinoVenta);

    void guardarTarifaFlete(Long idDestinoVenta, Dinero costoFletePorKg);
}
