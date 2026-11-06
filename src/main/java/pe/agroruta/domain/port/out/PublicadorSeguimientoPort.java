package pe.agroruta.domain.port.out;

import pe.agroruta.domain.model.AlertaEnvio;
import pe.agroruta.domain.model.Posicion;

/** Avisa en tiempo real a quienes están mirando el mapa de un envío. */
public interface PublicadorSeguimientoPort {

    void publicarPosicion(String identificadorNegocio, Long idEnvio, Posicion posicion, Long etaMinutos);

    void publicarAlerta(String identificadorNegocio, Long idEnvio, AlertaEnvio alerta);
}
