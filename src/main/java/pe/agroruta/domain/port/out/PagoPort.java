package pe.agroruta.domain.port.out;

import java.util.Optional;

import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.model.SolicitudCobro;

/** La pasarela de pago. Hoy puede ser el simulador o Culqi; cambiarla no toca el dominio (RNF-17). */
public interface PagoPort {

    /**
     * Cobra. Un rechazo (sin fondos, tarjeta vencida...) es un resultado, no un error.
     *
     * @throws pe.agroruta.domain.model.ServicioExternoNoDisponibleException si la pasarela no respondió y no se sabe si cobró
     */
    ResultadoCobro cobrar(SolicitudCobro solicitud);

    /** Consulta un cargo directamente a la pasarela. Es la forma de comprobar que una notificación es verdadera. */
    Optional<ResultadoCobro> consultarCargo(String idCargo);
}
