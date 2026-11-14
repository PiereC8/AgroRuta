package pe.agroruta.infrastructure.adapter.in.web;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import pe.agroruta.domain.model.AlertaEnvio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.Posicion;
import pe.agroruta.domain.model.Seguimiento;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.model.Trayecto;
import pe.agroruta.domain.model.TipoAlerta;
import pe.agroruta.domain.port.in.ConsultarSeguimientoUseCase;
import pe.agroruta.domain.port.in.EnviarEnlacePagoUseCase;
import pe.agroruta.domain.port.in.GenerarEnlacesEnvioUseCase;
import pe.agroruta.infrastructure.adapter.in.websocket.Canales;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt.DatosToken;

@RestController
@RequestMapping("/api/envios/{idEnvio}")
public class SeguimientoController {

    private final GenerarEnlacesEnvioUseCase generarEnlaces;
    private final ConsultarSeguimientoUseCase consultar;
    private final EnviarEnlacePagoUseCase enviarEnlacePago;
    /** Dirección de las páginas cuando no las sirve este backend (por ejemplo, Vercel). Vacía: la del pedido. */
    private final String urlPublica;

    public SeguimientoController(GenerarEnlacesEnvioUseCase generarEnlaces, ConsultarSeguimientoUseCase consultar,
                                 EnviarEnlacePagoUseCase enviarEnlacePago,
                                 @Value("${agroruta.web.url-publica:}") String urlPublica) {
        this.generarEnlaces = generarEnlaces;
        this.consultar = consultar;
        this.enviarEnlacePago = enviarEnlacePago;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    /**
     * Los tokens solo se muestran aquí, una vez. Con "tipo" se renueva solo ese enlace (CONDUCTOR o COMPRADOR); sin
     * él, los dos. Solo deja de valer el enlace anterior del mismo tipo.
     */
    @PostMapping("/enlaces")
    @ResponseStatus(HttpStatus.CREATED)
    public EnlacesRespuesta generarEnlaces(@PathVariable Long idEnvio,
                                           @RequestParam(name = "tipo", required = false) TipoEnlace tipo,
                                           @AuthenticationPrincipal DatosToken usuario) {
        var enlaces = generarEnlaces.generar(usuario.identificadorNegocio(), idEnvio, tipo);
        String rutaConductor = enlaces.tokenConductor() == null ? null
                : "/conductor.html#t=" + enlaces.tokenConductor();
        String rutaComprador = enlaces.tokenComprador() == null ? null
                : "/seguimiento.html#t=" + enlaces.tokenComprador();
        return new EnlacesRespuesta(enlaces.tokenConductor(), rutaConductor, enlaces.tokenComprador(), rutaComprador,
                enlaces.fechaExpiracion());
    }

    @GetMapping("/seguimiento")
    public SeguimientoRespuesta seguimiento(@PathVariable Long idEnvio, @AuthenticationPrincipal DatosToken usuario) {
        return SeguimientoRespuesta.de(consultar.obtener(idEnvio), usuario.identificadorNegocio());
    }

    @GetMapping("/recorrido")
    public List<PosicionRespuesta> recorrido(@PathVariable Long idEnvio) {
        return consultar.recorrido(idEnvio).stream().map(PosicionRespuesta::de).toList();
    }

    /** Manda al comprador, por correo, un enlace nuevo para pagar el pedido entregado. */
    @PostMapping("/enlace-pago/correo")
    public EnvioEnlaceRespuesta enviarEnlacePago(@PathVariable Long idEnvio, @RequestBody CorreoSolicitud solicitud,
                                                 @AuthenticationPrincipal DatosToken usuario) {
        var resultado = enviarEnlacePago.enviar(new EnviarEnlacePagoUseCase.Comando(usuario.identificadorNegocio(),
                idEnvio, solicitud.correo(), urlPublica.isBlank()
                ? ServletUriComponentsBuilder.fromCurrentContextPath().toUriString() : urlPublica));
        return new EnvioEnlaceRespuesta(resultado.correo(), resultado.urlPago(), resultado.fechaExpiracion());
    }

    @GetMapping("/alertas")
    public List<AlertaRespuesta> alertas(@PathVariable Long idEnvio) {
        return consultar.alertas(idEnvio).stream().map(AlertaRespuesta::de).toList();
    }

    public record CorreoSolicitud(String correo) {
    }

    public record EnvioEnlaceRespuesta(String correo, String urlPago, Instant fechaExpiracion) {
    }

    public record EnlacesRespuesta(String tokenConductor, String rutaConductor, String tokenComprador,
                                   String rutaComprador, Instant fechaExpiracion) {
    }

    public record PosicionRespuesta(double latitud, double longitud, Double precisionMetros, Instant fechaPosicion) {
        static PosicionRespuesta de(Posicion posicion) {
            return new PosicionRespuesta(posicion.coordenada().latitud(), posicion.coordenada().longitud(),
                    posicion.precisionMetros(), posicion.fechaPosicion());
        }
    }

    /** Puntos como [latitud, longitud]. La pantalla los usa para dibujar el camino y estimar el avance. */
    public record RutaRespuesta(List<double[]> puntos, double distanciaMetros, long duracionSegundos,
                                boolean porCarretera, Instant fechaCalculo) {
        public static RutaRespuesta de(Trayecto t) {
            return t == null ? null : new RutaRespuesta(t.puntos().stream()
                    .map(c -> new double[]{c.latitud(), c.longitud()}).toList(), t.distanciaMetros(),
                    t.duracionSegundos(), t.porCarretera(), t.fechaCalculo());
        }
    }

    public record AlertaRespuesta(TipoAlerta tipoAlerta, String descripcionAlerta, Instant fechaAlerta) {
        static AlertaRespuesta de(AlertaEnvio alerta) {
            return new AlertaRespuesta(alerta.tipoAlerta(), alerta.descripcionAlerta(), alerta.fechaAlerta());
        }
    }

    public record SeguimientoRespuesta(Long idEnvio, EstadoEnvio estadoEnvio, Instant fechaSalida,
                                       Instant fechaEntrega, String nombreDestino, String ciudadDestino,
                                       Double latitudDestino, Double longitudDestino,
                                       PosicionRespuesta ultimaPosicion, Long etaMinutos, Instant ahora,
                                       String canal, List<PosicionRespuesta> trazoReciente,
                                       RutaRespuesta ruta, Double velocidadKmh) {
        static SeguimientoRespuesta de(Seguimiento s, String identificadorNegocio) {
            return new SeguimientoRespuesta(s.envio().idEnvio(), s.envio().estadoEnvio(), s.envio().fechaSalida(),
                    s.envio().fechaEntrega(), s.nombreDestino(), s.ciudadDestino(),
                    s.coordenadaDestino() == null ? null : s.coordenadaDestino().latitud(),
                    s.coordenadaDestino() == null ? null : s.coordenadaDestino().longitud(),
                    s.ultimaPosicion() == null ? null : PosicionRespuesta.de(s.ultimaPosicion()), s.etaMinutos(),
                    s.ahora(), Canales.deEnvio(identificadorNegocio, s.envio().idEnvio()),
                    s.trazoReciente().stream().map(PosicionRespuesta::de).toList(), RutaRespuesta.de(s.ruta()),
                    s.velocidadKmh());
        }
    }
}
