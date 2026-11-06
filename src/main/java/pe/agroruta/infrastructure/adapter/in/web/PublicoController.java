package pe.agroruta.infrastructure.adapter.in.web;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.Seguimiento;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoAlerta;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.AutenticarEnlaceUseCase;
import pe.agroruta.domain.port.in.ConsultarSeguimientoUseCase;
import pe.agroruta.infrastructure.adapter.in.web.SeguimientoController.PosicionRespuesta;
import pe.agroruta.infrastructure.adapter.in.websocket.Canales;
import pe.agroruta.infrastructure.config.TenantContext;

/**
 * Lo que ve el comprador con su enlace, sin iniciar sesión. Muestra solo su envío y nada más:
 * ni precios, ni placas, ni datos de otros pedidos.
 */
@RestController
@RequestMapping("/api/publico")
public class PublicoController {

    public static final String ENCABEZADO_TOKEN = "X-Token-Seguimiento";

    private final AutenticarEnlaceUseCase enlaces;
    private final ConsultarSeguimientoUseCase consultar;

    public PublicoController(AutenticarEnlaceUseCase enlaces, ConsultarSeguimientoUseCase consultar) {
        this.enlaces = enlaces;
        this.consultar = consultar;
    }

    @GetMapping("/seguimiento")
    public SeguimientoPublicoRespuesta seguimiento(@RequestHeader(ENCABEZADO_TOKEN) String token) {
        SesionEnlace sesion = enlaces.resolver(token)
                .filter(s -> s.tipoEnlace() == TipoEnlace.COMPRADOR)
                .orElseThrow(() -> new RecursoNoEncontradoException("El enlace no es válido o ya venció"));
        return TenantContext.ejecutarEn(sesion.identificadorNegocio(), () -> {
            Seguimiento seguimiento = consultar.obtener(sesion.idEnvio());
            boolean llego = consultar.alertas(sesion.idEnvio()).stream()
                    .anyMatch(a -> a.tipoAlerta() == TipoAlerta.LLEGADA);
            return SeguimientoPublicoRespuesta.de(seguimiento, sesion, llego);
        });
    }

    public record SeguimientoPublicoRespuesta(EstadoEnvio estadoEnvio, Instant fechaSalida, Instant fechaEntrega,
                                              String ciudadDestino, Double latitudDestino, Double longitudDestino,
                                              PosicionRespuesta ultimaPosicion, Long etaMinutos,
                                              boolean llegoAlDestino, Instant ahora, String canal,
                                              List<PosicionRespuesta> trazoReciente) {
        static SeguimientoPublicoRespuesta de(Seguimiento s, SesionEnlace sesion, boolean llego) {
            return new SeguimientoPublicoRespuesta(s.envio().estadoEnvio(), s.envio().fechaSalida(),
                    s.envio().fechaEntrega(), s.ciudadDestino(),
                    s.coordenadaDestino() == null ? null : s.coordenadaDestino().latitud(),
                    s.coordenadaDestino() == null ? null : s.coordenadaDestino().longitud(),
                    s.ultimaPosicion() == null ? null : PosicionRespuesta.de(s.ultimaPosicion()), s.etaMinutos(),
                    llego, s.ahora(), Canales.deEnvio(sesion.identificadorNegocio(), sesion.idEnvio()),
                    s.trazoReciente().stream().map(PosicionRespuesta::de).toList());
        }
    }
}
