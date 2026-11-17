package pe.agroruta.domain.model;

// RFF-07: estados del pedido: pendiente → despachado → entregado, o cancelado.
public enum EstadoPedido {
    PENDIENTE,
    DESPACHADO,
    ENTREGADO,
    CANCELADO
}
