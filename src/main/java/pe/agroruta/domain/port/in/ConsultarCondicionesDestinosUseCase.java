package pe.agroruta.domain.port.in;

import java.util.Map;

import pe.agroruta.domain.model.CondicionesDestino;

// RFF-04: ver en la lista de destinos el flete y los precios que ya están fijados.
public interface ConsultarCondicionesDestinosUseCase {

    Map<Long, CondicionesDestino> vigentes();
}
