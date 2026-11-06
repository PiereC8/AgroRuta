package pe.agroruta.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.AutenticarEnlaceUseCase;
import pe.agroruta.domain.port.in.ConsultarEstadoPagoUseCase;
import pe.agroruta.domain.port.in.PagarPedidoUseCase;
import pe.agroruta.infrastructure.config.ConfiguracionPasarela;
import pe.agroruta.infrastructure.config.TenantContext;

@RestController
@RequestMapping("/api/publico/pago")
public class PublicoPagoController {

    private final AutenticarEnlaceUseCase enlaces;
    private final PagarPedidoUseCase pagar;
    private final ConsultarEstadoPagoUseCase consultarEstado;
    private final ConfiguracionPasarela pasarela;

    public PublicoPagoController(AutenticarEnlaceUseCase enlaces, PagarPedidoUseCase pagar,
                                 ConsultarEstadoPagoUseCase consultarEstado,
                                 ConfiguracionPasarela pasarela) {
        this.enlaces = enlaces;
        this.pagar = pagar;
        this.consultarEstado = consultarEstado;
        this.pasarela = pasarela;
    }

    @GetMapping
    public EstadoRespuesta estado(@RequestHeader(PublicoController.ENCABEZADO_TOKEN) String token) {
        SesionEnlace sesion = sesion(token);
        var estado = TenantContext.ejecutarEn(sesion.identificadorNegocio(), () -> consultarEstado.estado(sesion));
        return new EstadoRespuesta(estado.estadoEnvio(), estado.pagable(), estado.monto().monto(), "PEN",
                estado.descripcion(), pasarela.modo(), pasarela.llavePublica(),
                PagoRespuesta.de(estado.ultimoPago()), ComprobanteRespuesta.de(estado.comprobante()));
    }

    @PostMapping
    public ResultadoRespuesta pagar(@RequestHeader(PublicoController.ENCABEZADO_TOKEN) String token,
                                    @RequestHeader("Idempotency-Key") String claveIdempotencia,
                                    @Valid @RequestBody PagoSolicitud solicitud) {
        SesionEnlace sesion = sesion(token);
        var resultado = TenantContext.ejecutarEn(sesion.identificadorNegocio(),
                () -> pagar.pagar(new PagarPedidoUseCase.Comando(sesion, solicitud.tokenTarjeta(),
                        solicitud.email(), claveIdempotencia, solicitud.metodoPago(), solicitud.emisor())));
        return new ResultadoRespuesta(resultado.estadoPago(), resultado.mensaje(), resultado.monto().monto(),
                ComprobanteRespuesta.de(resultado.comprobante()));
    }

    private SesionEnlace sesion(String token) {
        return enlaces.resolver(token)
                .filter(s -> s.tipoEnlace() == TipoEnlace.COMPRADOR)
                .orElseThrow(() -> new RecursoNoEncontradoException("El enlace no es válido o ya venció"));
    }

    public record PagoSolicitud(@NotBlank String tokenTarjeta, @NotBlank String email, String metodoPago,
                                String emisor) {
    }

    public record PagoRespuesta(EstadoPago estadoPago, String mensaje, String marcaTarjeta, String ultimosCuatro,
                                Instant fecha) {
        static PagoRespuesta de(Pago pago) {
            return pago == null ? null : new PagoRespuesta(pago.estadoPago(), pago.mensajeUsuario(),
                    pago.marcaTarjeta(), pago.ultimosCuatro(), pago.fechaActualizacion());
        }
    }

    public record ComprobanteRespuesta(TipoComprobante tipo, String numero, EstadoComprobante estado,
                                       String enlacePdf, String enlaceXml, String mensaje) {
        static ComprobanteRespuesta de(Comprobante comprobante) {
            return comprobante == null ? null : new ComprobanteRespuesta(comprobante.tipoComprobante(),
                    comprobante.numeroCompleto(), comprobante.estadoComprobante(), comprobante.enlacePdf(),
                    comprobante.enlaceXml(), comprobante.mensajeSunat());
        }
    }

    public record EstadoRespuesta(EstadoEnvio estadoEnvio, boolean pagable, BigDecimal monto, String moneda,
                                  String descripcion, String modo, String llavePublica, PagoRespuesta ultimoPago,
                                  ComprobanteRespuesta comprobante) {
    }

    public record ResultadoRespuesta(EstadoPago estadoPago, String mensaje, BigDecimal monto,
                                     ComprobanteRespuesta comprobante) {
    }
}
