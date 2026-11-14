package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Un pedido toma una parte de un lote, o todo el lote, según lo que pide el comprador. */
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

    /**
     * @param kgDisponibles lo que queda del lote sin comprometer en otros pedidos (ver {@link ReglasLote})
     */
    public static Pedido nuevo(Long idComprador, Lote lote, BigDecimal kgDisponibles, Long idDestinoVenta,
                               BigDecimal cantidadKg, Dinero precioAcordadoPorKg, String usuarioRegistro,
                               Instant ahora) {
        if (kgDisponibles.signum() <= 0) {
            throw new ConflictoDominioException("El lote " + lote.idLote() + " no tiene kg disponibles");
        }
        if (cantidadKg == null || cantidadKg.signum() <= 0) {
            throw new DominioException("La cantidad del pedido debe ser mayor que cero");
        }
        if (cantidadKg.stripTrailingZeros().scale() > 3) {
            throw new DominioException("La cantidad del pedido admite hasta 3 decimales");
        }
        BigDecimal cantidad = cantidadKg.setScale(3, RoundingMode.UNNECESSARY);
        if (cantidad.compareTo(kgDisponibles) > 0) {
            throw new ConflictoDominioException("El lote " + lote.idLote() + " solo tiene "
                    + kgDisponibles.toPlainString() + " kg disponibles y el pedido pide " + cantidad.toPlainString());
        }
        return new Pedido(null, idComprador, lote.idLote(), idDestinoVenta, cantidad, precioAcordadoPorKg,
                EstadoPedido.PENDIENTE, usuarioRegistro, ahora);
    }

    /** Atajo para un pedido que toma todo el lote; solo vale si el lote no tiene ningún otro pedido. */
    public static Pedido nuevo(Long idComprador, Lote lote, Long idDestinoVenta, Dinero precioAcordadoPorKg,
                               String usuarioRegistro, Instant ahora) {
        if (lote.estadoLote() != EstadoLote.REGISTRADO) {
            throw new ConflictoDominioException("El lote " + lote.idLote() + " ya está " + lote.estadoLote());
        }
        return nuevo(idComprador, lote, lote.cantidadKg(), idDestinoVenta, lote.cantidadKg(), precioAcordadoPorKg,
                usuarioRegistro, ahora);
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
