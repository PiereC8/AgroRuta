package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Comprobante;

public interface EmitirComprobanteUseCase {

    /** Emite el comprobante de un pago aprobado. Si el proveedor no responde, queda pendiente para reintentarse. */
    Comprobante emitirPara(Long idPago);

    /** Reintenta los pendientes cuya hora llegó. Devuelve cuántos intentó. */
    int reintentarPendientes(int limite);

    Comprobante reintentar(Long idComprobante);

    /** Refresca el estado ante SUNAT preguntándole al proveedor (RF-20). */
    Comprobante consultar(Long idComprobante);
}
