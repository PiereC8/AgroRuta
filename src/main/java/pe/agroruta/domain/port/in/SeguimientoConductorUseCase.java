package pe.agroruta.domain.port.in;

import java.time.Instant;

import pe.agroruta.domain.model.SesionEnlace;

public interface SeguimientoConductorUseCase {

    enum ResultadoPosicion {
        ACEPTADA,
        DUPLICADA
    }

    void iniciar(SesionEnlace sesion, boolean gpsActivo, boolean permisoUbicacion, boolean consentimiento);

    ResultadoPosicion reportarPosicion(SesionEnlace sesion, double latitud, double longitud, Double precisionMetros,
                                       Instant fechaPosicion);

    void reportarEstadoGps(SesionEnlace sesion, boolean gpsActivo);
}
