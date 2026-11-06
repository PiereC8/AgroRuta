package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.SesionEnlace;

public interface ConsultarEstadoPagoUseCase {

    EstadoPagoPublico estado(SesionEnlace sesion);

    record EstadoPagoPublico(EstadoEnvio estadoEnvio, boolean pagable, Dinero monto, String descripcion,
                             Pago ultimoPago, Comprobante comprobante) {
    }
}
