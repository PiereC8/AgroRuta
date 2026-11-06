package pe.agroruta.domain.model;

public record ResultadoEmision(EstadoComprobante estado, String enlacePdf, String enlaceXml, String mensajeSunat) {
}
