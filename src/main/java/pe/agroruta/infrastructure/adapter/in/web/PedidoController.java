package pe.agroruta.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
import pe.agroruta.domain.model.EstadoCobro;
import pe.agroruta.domain.model.EstadoPedido;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.port.in.ConsultarCobroPedidosUseCase;
import pe.agroruta.domain.port.in.GestionarPedidosUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt.DatosToken;

// RFF-07: API /api/pedidos (registrar, listar y ver uno).
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final GestionarPedidosUseCase pedidos;
    private final ConsultarCobroPedidosUseCase cobros;

    public PedidoController(GestionarPedidosUseCase pedidos, ConsultarCobroPedidosUseCase cobros) {
        this.pedidos = pedidos;
        this.cobros = cobros;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoRespuesta registrar(@Valid @RequestBody PedidoSolicitud solicitud,
                                     @AuthenticationPrincipal DatosToken usuario) {
        return PedidoRespuesta.de(pedidos.registrar(new GestionarPedidosUseCase.Comando(
                solicitud.idComprador(), solicitud.idLote(), solicitud.idDestinoVenta(), solicitud.cantidadKg(),
                solicitud.precioAcordadoPorKg(), usuario.nombreUsuario())), EstadoCobro.SIN_ENTREGAR);
    }

    @GetMapping
    public List<PedidoRespuesta> listar(@RequestParam(defaultValue = "0") int pagina,
                                        @RequestParam(defaultValue = "50") int tamano) {
        List<Pedido> lista = pedidos.listar(pagina, tamano);
        Map<Long, EstadoCobro> cobrado = cobros.estados(lista.stream().map(Pedido::idPedido).toList());
        return lista.stream().map(p -> PedidoRespuesta.de(p, cobrado.get(p.idPedido()))).toList();
    }

    @GetMapping("/{idPedido}")
    public PedidoRespuesta obtener(@PathVariable Long idPedido) {
        Pedido pedido = pedidos.obtener(idPedido);
        return PedidoRespuesta.de(pedido, cobros.estados(List.of(idPedido)).get(idPedido));
    }

    /** cantidadKg vacío: el pedido toma todo lo que quede disponible del lote. */
    public record PedidoSolicitud(@NotNull Long idComprador, @NotNull Long idLote, @NotNull Long idDestinoVenta,
                                  BigDecimal cantidadKg, @NotNull BigDecimal precioAcordadoPorKg) {
    }

    public record PedidoRespuesta(Long idPedido, Long idComprador, Long idLote, Long idDestinoVenta,
                                  BigDecimal cantidadKg, BigDecimal precioAcordadoPorKg,
                                  BigDecimal ingresoAcordado, EstadoPedido estadoPedido, String usuarioRegistro,
                                  Instant fechaPedido, EstadoCobro cobrado) {
        static PedidoRespuesta de(Pedido pedido, EstadoCobro cobrado) {
            return new PedidoRespuesta(pedido.idPedido(), pedido.idComprador(), pedido.idLote(),
                    pedido.idDestinoVenta(), pedido.cantidadKg(), pedido.precioAcordadoPorKg().monto(),
                    pedido.ingresoAcordado().monto(), pedido.estadoPedido(), pedido.usuarioRegistro(),
                    pedido.fechaPedido(), cobrado == null ? EstadoCobro.SIN_ENTREGAR : cobrado);
        }
    }
}
