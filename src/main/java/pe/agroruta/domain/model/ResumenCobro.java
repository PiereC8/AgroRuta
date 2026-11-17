package pe.agroruta.domain.model;

/** aCobrar: ingreso real de la liquidación del pedido (null si no se entregó). cobrado: suma de pagos aprobados. */
public record ResumenCobro(Dinero aCobrar, Dinero cobrado) {
}
