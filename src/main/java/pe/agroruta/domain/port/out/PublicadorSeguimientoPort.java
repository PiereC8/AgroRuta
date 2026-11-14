package pe.agroruta.domain.port.out;

import pe.agroruta.domain.model.AlertaEnvio;
import pe.agroruta.domain.model.Posicion;

public interface PublicadorSeguimientoPort {

    void publicarPosicion(String identificadorNegocio, Long idEnvio, Posicion posicion, Long etaMinutos);

    /** Avisa que la ruta se calculó o cambió, para que las pantallas la vuelvan a pedir. */
    void publicarRuta(String identificadorNegocio, Long idEnvio);

    void publicarAlerta(String identificadorNegocio, Long idEnvio, AlertaEnvio alerta);
}
