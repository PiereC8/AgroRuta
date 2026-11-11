package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Un pedido cubre todo un lote: lo que pide el comprador es lo que pesa el lote. */
public record Pedido(Long idPedido, Long idComprador, Long idLote, Long idDestinoVenta, BigDecimal cantidadKg,
                     Dinero precioAcordadoPorKg, EstadoPedido estadoPedido, String usuarioRegistro,
                     Instant fechaPedido) {

    public Pedido {
        if (idComprador == null || idLote == null || idDestinoVenta == null || cantidadKg == null
                || estadoPedido == null || fechaPedido == null) {
            throw new DominioException("El pedido necesita comprador, lote, destino, cantidad, estado y fecha");
        }
        if (precioAcordadoPorKg == null || !precioAcordadoPorKg.esPositivo()) {
            throw new DominioException("El precio acordado por kg debe ser mayor que cero");
        }
        if (usuarioRegistro == null || usuarioRegistro.isBlank()) {
            throw new DominioException("El pedido necesita el usuario que lo registra");
        }
    }

    public static Pedido nuevo(Long idComprador, Lote lote, Long idDestinoVenta, Dinero precioAcordadoPorKg,
                               String usuarioRegistro, Instant ahora) {
        if (lote.estadoLote() != EstadoLote.REGISTRADO) {
            throw new ConflictoDominioException("El lote " + lote.idLote() + " ya está " + lote.estadoLote());
        }
        return new Pedido(null, idComprador, lote.idLote(), idDestinoVenta, lote.cantidadKg(),
                precioAcordadoPorKg, EstadoPedido.PENDIENTE, usuarioRegistro, ahora);
    }

    public Dinero ingresoAcordado() {
        return precioAcordadoPorKg.multiplicar(cantidadKg);
    }

    public Pedido despachado() {
        if (estadoPedido != EstadoPedido.PENDIENTE) {
            throw new ConflictoDominioException("El pedido " + idPedido + " ya está " + estadoPedido);
        }
        return new Pedido(idPedido, idComprador, idLote, idDestinoVenta, cantidadKg, precioAcordadoPorKg,
                EstadoPedido.DESPACHADO, usuarioRegistro, fechaPedido);
    }

    public Pedido entregado() {
        if (estadoPedido != EstadoPedido.DESPACHADO) {
            throw new ConflictoDominioException("El pedido " + idPedido + " está " + estadoPedido
                    + " y solo se entrega desde " + EstadoPedido.DESPACHADO);
        }
        return new Pedido(idPedido, idComprador, idLote, idDestinoVenta, cantidadKg, precioAcordadoPorKg,
                EstadoPedido.ENTREGADO, usuarioRegistro, fechaPedido);
    }

    /** Solo se cancela un pedido que todavía no tiene un envío en curso. */
    public Pedido cancelar() {
        if (estadoPedido != EstadoPedido.PENDIENTE) {
            throw new ConflictoDominioException("El pedido " + idPedido + " está " + estadoPedido
                    + ": cancela primero su envío");
        }
        return conEstado(EstadoPedido.CANCELADO);
    }

    /** Al cancelar su envío, el pedido vuelve a quedar pendiente de despacho. */
    public Pedido volverAPendiente() {
        if (estadoPedido != EstadoPedido.DESPACHADO) {
            throw new ConflictoDominioException("El pedido " + idPedido + " está " + estadoPedido
                    + " y solo vuelve a pendiente si estaba despachado");
        }
        return conEstado(EstadoPedido.PENDIENTE);
    }

    private Pedido conEstado(EstadoPedido nuevo) {
        return new Pedido(idPedido, idComprador, idLote, idDestinoVenta, cantidadKg, precioAcordadoPorKg, nuevo,
                usuarioRegistro, fechaPedido);
    }

    public Pedido conId(Long nuevoIdPedido) {
        return new Pedido(nuevoIdPedido, idComprador, idLote, idDestinoVenta, cantidadKg, precioAcordadoPorKg,
                estadoPedido, usuarioRegistro, fechaPedido);
    }
}
