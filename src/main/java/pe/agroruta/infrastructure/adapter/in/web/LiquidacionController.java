package pe.agroruta.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.in.ConsultarLiquidacionesUseCase;
import pe.agroruta.domain.port.in.RegistrarEntregaUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt.DatosToken;

@RestController
public class LiquidacionController {

    private final RegistrarEntregaUseCase registrarEntrega;
    private final ConsultarLiquidacionesUseCase consultarLiquidaciones;

    public LiquidacionController(RegistrarEntregaUseCase registrarEntrega,
                                 ConsultarLiquidacionesUseCase consultarLiquidaciones) {
        this.registrarEntrega = registrarEntrega;
        this.consultarLiquidaciones = consultarLiquidaciones;
    }

    @PostMapping("/api/envios/{idEnvio}/entrega")
    @ResponseStatus(HttpStatus.CREATED)
    public LiquidacionRespuesta registrarEntrega(@PathVariable Long idEnvio,
                                                 @Valid @RequestBody EntregaSolicitud solicitud,
                                                 @AuthenticationPrincipal DatosToken usuario) {
        return LiquidacionRespuesta.de(registrarEntrega.registrar(new RegistrarEntregaUseCase.Comando(
                idEnvio, solicitud.cantidadRecibidaKg(), solicitud.mermaKg(), solicitud.rechazoKg(),
                usuario.nombreUsuario())), usuario);
    }

    @GetMapping("/api/lotes/{idLote}/liquidacion")
    public LiquidacionRespuesta obtenerPorLote(@PathVariable Long idLote,
                                               @AuthenticationPrincipal DatosToken usuario) {
        return LiquidacionRespuesta.de(consultarLiquidaciones.obtenerPorLote(idLote), usuario);
    }

    @GetMapping("/api/liquidaciones")
    public List<LiquidacionRespuesta> listar(@RequestParam(defaultValue = "0") int pagina,
                                             @RequestParam(defaultValue = "50") int tamano,
                                             @AuthenticationPrincipal DatosToken usuario) {
        return consultarLiquidaciones.listar(pagina, tamano).stream()
                .map(l -> LiquidacionRespuesta.de(l, usuario)).toList();
    }

    public record EntregaSolicitud(@NotNull BigDecimal cantidadRecibidaKg, @NotNull BigDecimal mermaKg,
                                   @NotNull BigDecimal rechazoKg) {
    }

    /**
     * El Operador cobra, pero no ve ganancia, flete ni pérdida: esos campos faltan por completo (no van en null).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LiquidacionRespuesta(Long idLiquidacion, Long idLote, Long idPedido, Long idEnvio,
                                       BigDecimal cantidadDespachadaKg, BigDecimal cantidadRecibidaKg,
                                       BigDecimal mermaKg, BigDecimal rechazoKg, BigDecimal precioAcordadoPorKg,
                                       BigDecimal costoFletePorKg, BigDecimal ingresoReal,
                                       BigDecimal costoFleteTotal, BigDecimal gananciaReal,
                                       BigDecimal gananciaEsperada, BigDecimal perdidaPorMermaYRechazo,
                                       String usuarioEntrega, Instant fechaEntrega) {
        static LiquidacionRespuesta de(Liquidacion l, DatosToken usuario) {
            boolean dueno = usuario != null && usuario.rolUsuario() == Rol.DUENO;
            return new LiquidacionRespuesta(l.idLiquidacion(), l.idLote(), l.idPedido(), l.idEnvio(),
                    l.cantidadDespachadaKg(), l.cantidadRecibidaKg(), l.mermaKg(), l.rechazoKg(),
                    l.precioAcordadoPorKg().monto(), dueno ? l.costoFletePorKg().monto() : null,
                    l.ingresoReal().monto(),
                    dueno ? l.costoFleteTotal().monto() : null,
                    dueno ? l.gananciaReal().monto() : null,
                    dueno ? l.gananciaEsperada().monto() : null,
                    dueno ? l.perdidaPorMermaYRechazo().monto() : null, l.usuarioEntrega(), l.fechaEntrega());
        }
    }
}
