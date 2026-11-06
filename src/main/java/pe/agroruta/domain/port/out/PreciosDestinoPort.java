package pe.agroruta.domain.port.out;

import java.util.Map;

import pe.agroruta.domain.model.Dinero;

public interface PreciosDestinoPort {

    /** Crea o reemplaza el precio esperado por kg de un producto en un destino. */
    void guardarPrecioEsperado(Long idDestinoVenta, String productoLote, Dinero precioPorKg);

    /** Precio esperado por kg de un producto, por destino. Los destinos sin precio no aparecen. */
    Map<Long, Dinero> preciosEsperadosPorKg(String productoLote);

    void guardarTarifaFlete(Long idDestinoVenta, Dinero costoFletePorKg);
}
