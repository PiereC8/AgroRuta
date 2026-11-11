package pe.agroruta.infrastructure.adapter.in.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pe.agroruta.domain.port.in.CancelarOperacionUseCase;
import pe.agroruta.domain.port.in.CorregirEntregaUseCase;
import pe.agroruta.infrastructure.adapter.in.web.EnvioController.EnvioRespuesta;
import pe.agroruta.infrastructure.adapter.in.web.LiquidacionController.EntregaSolicitud;
import pe.agroruta.infrastructure.adapter.in.web.LiquidacionController.LiquidacionRespuesta;
import pe.agroruta.infrastructure.adapter.in.web.PedidoController.PedidoRespuesta;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt.DatosToken;

/** Salidas cuando algo sale mal: cancelar un envío o un pedido y corregir una entrega antes del pago. */
@RestController
public class CorreccionesController {

    private final CancelarOperacionUseCase cancelar;
    private final CorregirEntregaUseCase corregirEntrega;

    public CorreccionesController(CancelarOperacionUseCase cancelar, CorregirEntregaUseCase corregirEntrega) {
        this.cancelar = cancelar;
        this.corregirEntrega = corregirEntrega;
    }

    @PostMapping("/api/envios/{idEnvio}/cancelar")
    public EnvioRespuesta cancelarEnvio(@PathVariable Long idEnvio, @AuthenticationPrincipal DatosToken usuario) {
        return EnvioRespuesta.de(cancelar.cancelarEnvio(usuario.identificadorNegocio(), idEnvio,
                usuario.nombreUsuario()));
    }

    @PostMapping("/api/pedidos/{idPedido}/cancelar")
    public PedidoRespuesta cancelarPedido(@PathVariable Long idPedido, @AuthenticationPrincipal DatosToken usuario) {
        return PedidoRespuesta.de(cancelar.cancelarPedido(idPedido, usuario.nombreUsuario()));
    }

    @PutMapping("/api/envios/{idEnvio}/entrega")
    public LiquidacionRespuesta corregirEntrega(@PathVariable Long idEnvio,
                                                @Valid @RequestBody EntregaSolicitud solicitud,
                                                @AuthenticationPrincipal DatosToken usuario) {
        return LiquidacionRespuesta.de(corregirEntrega.corregir(new CorregirEntregaUseCase.Comando(idEnvio,
                solicitud.cantidadRecibidaKg(), solicitud.mermaKg(), solicitud.rechazoKg(), usuario.nombreUsuario())));
    }
}
