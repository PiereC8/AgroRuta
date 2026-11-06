package pe.agroruta.domain.port.out;

import pe.agroruta.domain.model.DatosComprobante;
import pe.agroruta.domain.model.ResultadoEmision;
import pe.agroruta.domain.model.TipoComprobante;

/** El proveedor de comprobantes electrónicos (OSE/PSE). Cambiarlo no toca el dominio (RNF-17). */
public interface ComprobantePort {

    /**
     * Emite el comprobante. Si ya existía con esa serie y número, devuelve el existente.
     *
     * @throws pe.agroruta.domain.model.ServicioExternoNoDisponibleException si el proveedor no respondió
     */
    ResultadoEmision emitir(DatosComprobante datos);

    ResultadoEmision consultar(TipoComprobante tipo, String serie, int numero);
}
