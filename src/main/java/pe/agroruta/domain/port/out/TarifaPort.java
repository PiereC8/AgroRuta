package pe.agroruta.domain.port.out;

import java.util.Collection;
import java.util.Map;

import pe.agroruta.domain.model.Dinero;

/** Fuente de las tarifas de flete. Hoy es una tabla manual; mañana puede ser una API externa. */
public interface TarifaPort {

    /** Costo de flete por kg de cada destino pedido. Los destinos sin tarifa no aparecen en el resultado. */
    Map<Long, Dinero> costosFletePorKg(Collection<Long> idsDestinoVenta);
}
