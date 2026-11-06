package pe.agroruta.domain.port.out;

import pe.agroruta.domain.model.AlertaEnvio;
import pe.agroruta.domain.model.Posicion;

public interface PublicadorSeguimientoPort {

    void publicarPosicion(String identificadorNegocio, Long idEnvio, Posicion posicion, Long etaMinutos);

    void publicarAlerta(String identificadorNegocio, Long idEnvio, AlertaEnvio alerta);
}
