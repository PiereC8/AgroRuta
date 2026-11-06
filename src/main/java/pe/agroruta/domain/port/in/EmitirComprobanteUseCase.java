package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Comprobante;

public interface EmitirComprobanteUseCase {

    Comprobante emitirPara(Long idPago);

    int reintentarPendientes(int limite);

    Comprobante reintentar(Long idComprobante);

    Comprobante consultar(Long idComprobante);
}
