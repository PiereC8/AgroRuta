package pe.agroruta.domain.model;

/** Lo que se imprime de un comprobante: su número y estado ante SUNAT, y los datos que se le enviaron. */
public record ComprobanteImpreso(Comprobante comprobante, DatosComprobante datos) {
}
