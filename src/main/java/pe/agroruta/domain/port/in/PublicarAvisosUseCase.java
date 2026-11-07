package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.HechoDelNegocio;

public interface PublicarAvisosUseCase {

    /** Deja en la bandeja los correos que merece el hecho. Nunca lanza error: un aviso no puede romper una operación. */
    void publicar(HechoDelNegocio hecho);
}
