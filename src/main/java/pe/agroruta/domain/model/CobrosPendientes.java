package pe.agroruta.domain.model;

/** Lo entregado que todavía no tiene un pago aprobado. */
public record CobrosPendientes(Dinero monto, int cantidad) {
}
