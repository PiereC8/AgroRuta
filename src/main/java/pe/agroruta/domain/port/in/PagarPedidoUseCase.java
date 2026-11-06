package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.SesionEnlace;

public interface PagarPedidoUseCase {

    /** Cobra el pedido del enlace. Repetir la misma clave de operación nunca cobra dos veces. */
    ResultadoPago pagar(Comando comando);

    EstadoPagoPublico estado(SesionEnlace sesion);

    record Comando(SesionEnlace sesion, String tokenTarjeta, String email, String claveIdempotencia,
                   String metodoPago, String emisor) {

        public Comando(SesionEnlace sesion, String tokenTarjeta, String email, String claveIdempotencia) {
            this(sesion, tokenTarjeta, email, claveIdempotencia, null, null);
        }
    }

    record ResultadoPago(Long idPago, EstadoPago estadoPago, String mensaje, Dinero monto, Comprobante comprobante) {
    }

    record EstadoPagoPublico(EstadoEnvio estadoEnvio, boolean pagable, Dinero monto, String descripcion,
                             Pago ultimoPago, Comprobante comprobante) {
    }
}
