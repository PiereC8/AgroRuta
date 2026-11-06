package pe.agroruta.domain.port.in;

import java.time.Instant;

import pe.agroruta.domain.model.SesionEnlace;

public interface SeguimientoConductorUseCase {

    enum ResultadoPosicion {
        ACEPTADA,
        DUPLICADA
    }

    /** Antes de compartir la ubicación: consentimiento del conductor y GPS activo con permiso (RF-24). */
    void iniciar(SesionEnlace sesion, boolean gpsActivo, boolean permisoUbicacion, boolean consentimiento);

    ResultadoPosicion reportarPosicion(SesionEnlace sesion, double latitud, double longitud, Double precisionMetros,
                                       Instant fechaPosicion);

    /** Si el GPS se desactiva durante la ruta, se avisa al Operador (RF-24). */
    void reportarEstadoGps(SesionEnlace sesion, boolean gpsActivo);
}
