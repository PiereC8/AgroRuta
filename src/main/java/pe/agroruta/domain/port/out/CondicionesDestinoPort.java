package pe.agroruta.domain.port.out;

import java.util.Map;

import pe.agroruta.domain.model.CondicionesDestino;

public interface CondicionesDestinoPort {

    /** Flete y precios vigentes de todos los destinos; los que no tienen ninguno no aparecen. */
    Map<Long, CondicionesDestino> vigentes();
}
