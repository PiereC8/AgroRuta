package pe.agroruta.application.service;

import java.util.Map;

import pe.agroruta.domain.model.CondicionesDestino;
import pe.agroruta.domain.port.in.ConsultarCondicionesDestinosUseCase;
import pe.agroruta.domain.port.out.CondicionesDestinoPort;

public class ConsultarCondicionesDestinosService implements ConsultarCondicionesDestinosUseCase {

    private final CondicionesDestinoPort condiciones;

    public ConsultarCondicionesDestinosService(CondicionesDestinoPort condiciones) {
        this.condiciones = condiciones;
    }

    @Override
    public Map<Long, CondicionesDestino> vigentes() {
        return condiciones.vigentes();
    }
}
