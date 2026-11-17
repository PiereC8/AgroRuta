package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.Pedido;

// RFF-07 y RFF-08: puerto de entrada para cancelar pedidos y envíos.
/** Salidas cuando algo falla: cancelar un envío (libera lote y vehículo) o un pedido (libera el lote). */
public interface CancelarOperacionUseCase {

    /** motivo: obligatorio si el envío va en ruta; opcional si solo estaba programado. */
    Envio cancelarEnvio(String identificadorNegocio, Long idEnvio, String motivo, String usuario);

    Pedido cancelarPedido(Long idPedido, String usuario);
}
