package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Panel;
import pe.agroruta.domain.model.Rol;

// RFF-18: puerto de entrada del panel.
public interface ConsultarPanelUseCase {

    Panel consultar(Rol rol);
}
