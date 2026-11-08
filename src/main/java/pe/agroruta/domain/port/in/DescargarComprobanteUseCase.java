package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Archivo;
import pe.agroruta.domain.model.SesionEnlace;

public interface DescargarComprobanteUseCase {

    /** PDF del comprobante de un pago, para el Dueño o el Operador. */
    Archivo pdfDePago(Long idPago);

    /** PDF del comprobante del pedido de un enlace de comprador. */
    Archivo pdfDelComprador(SesionEnlace sesion);
}
