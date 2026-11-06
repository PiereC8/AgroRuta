package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.Plan;

public interface AltaNegocioUseCase {

    Negocio darDeAlta(Comando comando);

    record Comando(String slug, String nombre, Plan plan) {
    }
}
