package pe.agroruta.domain.model;

public record SeriesComprobante(String boleta, String factura) {

    public SeriesComprobante {
        if (boleta == null || !boleta.matches("[A-Z0-9]{4}") || factura == null || !factura.matches("[A-Z0-9]{4}")) {
            throw new DominioException("Las series de boleta y factura deben tener 4 caracteres, por ejemplo B001 y F001");
        }
    }

    public String paraTipo(TipoComprobante tipo) {
        return tipo == TipoComprobante.FACTURA ? factura : boleta;
    }
}
