package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.Notificacion;

public interface ConsultarNotificacionesUseCase {

    List<Notificacion> recientes(int limite);
}
