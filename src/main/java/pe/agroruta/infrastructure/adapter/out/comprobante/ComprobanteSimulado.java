package pe.agroruta.infrastructure.adapter.out.comprobante;

import pe.agroruta.domain.model.DatosComprobante;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.ResultadoEmision;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.domain.port.out.ComprobantePort;

/** Emisor de mentira: acepta todo. No tiene validez ante SUNAT; sirve para desarrollar y demostrar sin cuenta. */
public class ComprobanteSimulado implements ComprobantePort {

    private static final String AVISO = "Comprobante de prueba del simulador: no tiene validez ante SUNAT";

    @Override
    public ResultadoEmision emitir(DatosComprobante datos) {
        return new ResultadoEmision(EstadoComprobante.ACEPTADO, null, null, AVISO);
    }

    @Override
    public ResultadoEmision consultar(TipoComprobante tipo, String serie, int numero) {
        return new ResultadoEmision(EstadoComprobante.ACEPTADO, null, null, AVISO);
    }
}
