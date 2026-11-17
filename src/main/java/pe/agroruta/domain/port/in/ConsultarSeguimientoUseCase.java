package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.AlertaEnvio;
import pe.agroruta.domain.model.Posicion;
import pe.agroruta.domain.model.Seguimiento;

public interface ConsultarSeguimientoUseCase {

    int POSICIONES_DEL_TRAZO = 60;
    int POSICIONES_DEL_RECORRIDO = 5000;

    Seguimiento obtener(Long idEnvio);

    List<Posicion> recorrido(Long idEnvio);

    List<AlertaEnvio> alertas(Long idEnvio);

    /** Para la lista de envíos: alerta «sin señal» de cada envío en ruta que la tenga. */
    java.util.Map<Long, AlertaEnvio> sinSenal(List<pe.agroruta.domain.model.Envio> envios);
}
