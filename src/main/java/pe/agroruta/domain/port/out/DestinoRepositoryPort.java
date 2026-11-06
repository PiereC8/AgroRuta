package pe.agroruta.domain.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DestinoVenta;

public interface DestinoRepositoryPort {

    boolean existePorNombre(String nombreDestino);

    DestinoVenta guardar(DestinoVenta destino);

    Optional<DestinoVenta> buscarPorId(Long idDestinoVenta);

    List<DestinoVenta> listarActivos();

    /** Crea o reemplaza el precio esperado por kg de un producto en un destino. */
    void guardarPrecioEsperado(Long idDestinoVenta, String productoLote, Dinero precioPorKg);

    /** Precio esperado por kg de un producto, por destino. Los destinos sin precio no aparecen. */
    Map<Long, Dinero> preciosEsperadosPorKg(String productoLote);

    /** Guarda la tarifa en la tabla manual que lee el adaptador de tarifas actual. */
    void guardarTarifaFlete(Long idDestinoVenta, Dinero costoFletePorKg);
}
