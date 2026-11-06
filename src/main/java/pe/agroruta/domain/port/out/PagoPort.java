package pe.agroruta.domain.port.out;

import java.util.Optional;

import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.model.SolicitudCobro;

public interface PagoPort {

    /**
     * Cobra. Un rechazo (sin fondos, tarjeta vencida...) es un resultado, no un error.
     *
     * @throws pe.agroruta.domain.model.ServicioExternoNoDisponibleException si la pasarela no respondió y no se sabe si cobró
     */
    ResultadoCobro cobrar(SolicitudCobro solicitud);

    Optional<ResultadoCobro> consultarCargo(String idCargo);
}
