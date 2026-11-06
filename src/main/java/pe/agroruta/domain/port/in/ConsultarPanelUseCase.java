package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Panel;
import pe.agroruta.domain.model.Rol;

public interface ConsultarPanelUseCase {

    Panel consultar(Rol rol);
}
