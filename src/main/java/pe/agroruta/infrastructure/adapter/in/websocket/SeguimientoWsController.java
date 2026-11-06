package pe.agroruta.infrastructure.adapter.in.websocket;

import java.security.Principal;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.port.in.SeguimientoConductorUseCase;
import pe.agroruta.infrastructure.config.TenantContext;

/** Recibe lo que manda el celular del conductor y le responde con un acuse por cada mensaje. */
@Controller
public class SeguimientoWsController {

    private static final Logger REGISTRO = LoggerFactory.getLogger(SeguimientoWsController.class);

    public record IniciarMensaje(boolean gpsActivo, boolean permisoUbicacion, boolean consentimiento) {
    }

    public record PosicionMensaje(String idLocal, double latitud, double longitud, Double precisionMetros,
                                  long fechaMs) {
    }

    public record EstadoGpsMensaje(boolean gpsActivo) {
    }

    /** estado: ACEPTADA, DUPLICADA o RECHAZADA. Con ACEPTADA o DUPLICADA el celular puede borrar su copia. */
    public record AcuseMensaje(String tipo, String idLocal, String estado, String detalle) {
    }

    public record ErrorMensaje(String detalle) {
    }

    private final SeguimientoConductorUseCase seguimiento;

    public SeguimientoWsController(SeguimientoConductorUseCase seguimiento) {
        this.seguimiento = seguimiento;
    }

    @MessageMapping("/iniciar")
    @SendToUser("/queue/acuses")
    public AcuseMensaje iniciar(IniciarMensaje mensaje, Principal principal) {
        SesionEnlace sesion = sesion(principal);
        try {
            TenantContext.ejecutarEn(sesion.identificadorNegocio(), () -> {
                seguimiento.iniciar(sesion, mensaje.gpsActivo(), mensaje.permisoUbicacion(),
                        mensaje.consentimiento());
                return null;
            });
            return new AcuseMensaje("INICIO", null, "ACEPTADA", null);
        } catch (DominioException e) {
            return new AcuseMensaje("INICIO", null, "RECHAZADA", e.getMessage());
        }
    }

    @MessageMapping("/posicion")
    @SendToUser("/queue/acuses")
    public AcuseMensaje posicion(PosicionMensaje mensaje, Principal principal) {
        SesionEnlace sesion = sesion(principal);
        try {
            var resultado = TenantContext.ejecutarEn(sesion.identificadorNegocio(),
                    () -> seguimiento.reportarPosicion(sesion, mensaje.latitud(), mensaje.longitud(),
                            mensaje.precisionMetros(), Instant.ofEpochMilli(mensaje.fechaMs())));
            return new AcuseMensaje("POSICION", mensaje.idLocal(), resultado.name(), null);
        } catch (DominioException e) {
            // Rechazo definitivo: reenviar no cambiaría nada, así que el celular puede descartarla.
            return new AcuseMensaje("POSICION", mensaje.idLocal(), "RECHAZADA", e.getMessage());
        }
    }

    @MessageMapping("/estado-gps")
    @SendToUser("/queue/acuses")
    public AcuseMensaje estadoGps(EstadoGpsMensaje mensaje, Principal principal) {
        SesionEnlace sesion = sesion(principal);
        try {
            TenantContext.ejecutarEn(sesion.identificadorNegocio(), () -> {
                seguimiento.reportarEstadoGps(sesion, mensaje.gpsActivo());
                return null;
            });
            return new AcuseMensaje("GPS", null, "ACEPTADA", null);
        } catch (DominioException e) {
            return new AcuseMensaje("GPS", null, "RECHAZADA", e.getMessage());
        }
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errores")
    public ErrorMensaje error(Exception excepcion) {
        REGISTRO.error("Error al procesar un mensaje del conductor", excepcion);
        return new ErrorMensaje("No se pudo procesar el mensaje, se reintentará");
    }

    private static SesionEnlace sesion(Principal principal) {
        if (principal instanceof UsuarioWs usuario && usuario.esConductor()) {
            return usuario.sesionEnlace();
        }
        throw new DominioException("Solo el conductor puede enviar su ubicación");
    }
}
