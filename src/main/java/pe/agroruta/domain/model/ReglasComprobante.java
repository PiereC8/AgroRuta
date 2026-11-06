package pe.agroruta.domain.model;

/** Qué comprobante corresponde: factura si el comprador tiene RUC, boleta si tiene DNI o ninguno. */
public final class ReglasComprobante {

    /** Desde este monto, una boleta debe identificar al cliente. */
    public static final Dinero MONTO_QUE_EXIGE_DOCUMENTO = Dinero.de("700.00");

    private ReglasComprobante() {
    }

    public static TipoComprobante tipoPara(Comprador comprador, Dinero monto) {
        String documento = comprador.documentoComprador();
        if (documento != null && documento.length() == 11) {
            return TipoComprobante.FACTURA;
        }
        if (documento == null && monto.compareTo(MONTO_QUE_EXIGE_DOCUMENTO) >= 0) {
            throw new DominioException("Para montos desde S/ " + MONTO_QUE_EXIGE_DOCUMENTO.monto().toPlainString()
                    + " el comprador necesita un DNI o un RUC registrado");
        }
        return TipoComprobante.BOLETA;
    }

    public static DatosComprobante.Cliente clienteDe(Comprador comprador, String emailPagador) {
        String documento = comprador.documentoComprador();
        if (documento == null) {
            return new DatosComprobante.Cliente("-", "-", "CLIENTE VARIOS", emailPagador);
        }
        return new DatosComprobante.Cliente(documento.length() == 11 ? "6" : "1", documento,
                comprador.nombreComprador(), emailPagador);
    }
}
