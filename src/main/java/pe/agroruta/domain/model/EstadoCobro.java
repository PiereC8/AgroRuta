package pe.agroruta.domain.model;

/** Si un pedido ya se cobró, comparando los pagos aprobados con lo que hay que cobrar según su liquidación. */
public enum EstadoCobro {
    /** Todavía no se entregó: no hay liquidación ni nada que cobrar. */
    SIN_ENTREGAR,
    POR_COBRAR,
    PARCIAL,
    COBRADO;

    public static EstadoCobro segun(ResumenCobro resumen) {
        if (resumen == null || resumen.aCobrar() == null) {
            return SIN_ENTREGAR;
        }
        Dinero cobrado = resumen.cobrado() == null ? Dinero.CERO : resumen.cobrado();
        if (cobrado.compareTo(resumen.aCobrar()) >= 0) {
            return COBRADO;
        }
        return cobrado.esPositivo() ? PARCIAL : POR_COBRAR;
    }
}
