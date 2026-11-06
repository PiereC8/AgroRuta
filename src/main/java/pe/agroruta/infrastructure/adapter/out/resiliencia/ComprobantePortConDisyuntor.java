package pe.agroruta.infrastructure.adapter.out.resiliencia;

import pe.agroruta.domain.model.DatosComprobante;
import pe.agroruta.domain.model.ResultadoEmision;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.domain.port.out.ComprobantePort;

/** Decorator: el mismo proveedor de comprobantes, con un disyuntor delante. */
public class ComprobantePortConDisyuntor implements ComprobantePort {

    private final ComprobantePort proveedor;
    private final Disyuntor disyuntor;

    public ComprobantePortConDisyuntor(ComprobantePort proveedor, Disyuntor disyuntor) {
        this.proveedor = proveedor;
        this.disyuntor = disyuntor;
    }

    @Override
    public ResultadoEmision emitir(DatosComprobante datos) {
        return disyuntor.ejecutar(() -> proveedor.emitir(datos));
    }

    @Override
    public ResultadoEmision consultar(TipoComprobante tipo, String serie, int numero) {
        return disyuntor.ejecutar(() -> proveedor.consultar(tipo, serie, numero));
    }
}
