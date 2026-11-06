package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Panel;
import pe.agroruta.domain.model.Rol;

public interface ConsultarPanelUseCase {

    /** Arma el panel del mes actual (hora de Lima). Solo el Dueño recibe la ganancia. */
    Panel consultar(Rol rol);
}
