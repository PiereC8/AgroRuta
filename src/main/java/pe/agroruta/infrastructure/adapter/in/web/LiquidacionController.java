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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import pe.agroruta.domain.model.Liquidacion;
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
                usuario.nombreUsuario())));
    }

    @GetMapping("/api/lotes/{idLote}/liquidacion")
    public LiquidacionRespuesta obtenerPorLote(@PathVariable Long idLote) {
        return LiquidacionRespuesta.de(consultarLiquidaciones.obtenerPorLote(idLote));
    }

    @GetMapping("/api/liquidaciones")
    public List<LiquidacionRespuesta> listar(@RequestParam(defaultValue = "0") int pagina,
                                             @RequestParam(defaultValue = "50") int tamano) {
        return consultarLiquidaciones.listar(pagina, tamano).stream().map(LiquidacionRespuesta::de).toList();
    }

    public record EntregaSolicitud(@NotNull BigDecimal cantidadRecibidaKg, @NotNull BigDecimal mermaKg,
                                   @NotNull BigDecimal rechazoKg) {
    }

    public record LiquidacionRespuesta(Long idLiquidacion, Long idLote, Long idPedido, Long idEnvio,
                                       BigDecimal cantidadDespachadaKg, BigDecimal cantidadRecibidaKg,
                                       BigDecimal mermaKg, BigDecimal rechazoKg, BigDecimal precioAcordadoPorKg,
                                       BigDecimal costoFletePorKg, BigDecimal ingresoReal,
                                       BigDecimal costoFleteTotal, BigDecimal gananciaReal,
                                       BigDecimal gananciaEsperada, BigDecimal perdidaPorMermaYRechazo,
                                       String usuarioEntrega, Instant fechaEntrega) {
        static LiquidacionRespuesta de(Liquidacion l) {
            return new LiquidacionRespuesta(l.idLiquidacion(), l.idLote(), l.idPedido(), l.idEnvio(),
                    l.cantidadDespachadaKg(), l.cantidadRecibidaKg(), l.mermaKg(), l.rechazoKg(),
                    l.precioAcordadoPorKg().monto(), l.costoFletePorKg().monto(), l.ingresoReal().monto(),
                    l.costoFleteTotal().monto(), l.gananciaReal().monto(), l.gananciaEsperada().monto(),
                    l.perdidaPorMermaYRechazo().monto(), l.usuarioEntrega(), l.fechaEntrega());
        }
    }
}
