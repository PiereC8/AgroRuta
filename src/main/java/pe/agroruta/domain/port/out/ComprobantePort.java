package pe.agroruta.domain.port.out;

import pe.agroruta.domain.model.DatosComprobante;
import pe.agroruta.domain.model.ResultadoEmision;
import pe.agroruta.domain.model.TipoComprobante;

public interface ComprobantePort {

    ResultadoEmision emitir(DatosComprobante datos);

    ResultadoEmision consultar(TipoComprobante tipo, String serie, int numero);
}
