package pe.agroruta.domain.port.out;

import java.util.Collection;
import java.util.Map;

import pe.agroruta.domain.model.Dinero;

public interface TarifaPort {

    Map<Long, Dinero> costosFletePorKg(Collection<Long> idsDestinoVenta);
}
