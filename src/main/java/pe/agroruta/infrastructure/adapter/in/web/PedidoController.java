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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import pe.agroruta.domain.model.EstadoPedido;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.port.in.GestionarPedidosUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt.DatosToken;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final GestionarPedidosUseCase pedidos;

    public PedidoController(GestionarPedidosUseCase pedidos) {
        this.pedidos = pedidos;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoRespuesta registrar(@Valid @RequestBody PedidoSolicitud solicitud,
                                     @AuthenticationPrincipal DatosToken usuario) {
        return PedidoRespuesta.de(pedidos.registrar(new GestionarPedidosUseCase.Comando(
                solicitud.idComprador(), solicitud.idLote(), solicitud.idDestinoVenta(),
                solicitud.precioAcordadoPorKg(), usuario.nombreUsuario())));
    }

    @GetMapping
    public List<PedidoRespuesta> listar(@RequestParam(defaultValue = "0") int pagina,
                                        @RequestParam(defaultValue = "50") int tamano) {
        return pedidos.listar(pagina, tamano).stream().map(PedidoRespuesta::de).toList();
    }

    @GetMapping("/{idPedido}")
    public PedidoRespuesta obtener(@PathVariable Long idPedido) {
        return PedidoRespuesta.de(pedidos.obtener(idPedido));
    }

    public record PedidoSolicitud(@NotNull Long idComprador, @NotNull Long idLote, @NotNull Long idDestinoVenta,
                                  @NotNull BigDecimal precioAcordadoPorKg) {
    }

    public record PedidoRespuesta(Long idPedido, Long idComprador, Long idLote, Long idDestinoVenta,
                                  BigDecimal cantidadKg, BigDecimal precioAcordadoPorKg,
                                  BigDecimal ingresoAcordado, EstadoPedido estadoPedido, String usuarioRegistro,
                                  Instant fechaPedido) {
        static PedidoRespuesta de(Pedido pedido) {
            return new PedidoRespuesta(pedido.idPedido(), pedido.idComprador(), pedido.idLote(),
                    pedido.idDestinoVenta(), pedido.cantidadKg(), pedido.precioAcordadoPorKg().monto(),
                    pedido.ingresoAcordado().monto(), pedido.estadoPedido(), pedido.usuarioRegistro(),
                    pedido.fechaPedido());
        }
    }
}
