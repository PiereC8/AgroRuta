package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.Pedido;

/** Salidas cuando algo falla: cancelar un envío (libera lote y vehículo) o un pedido (libera el lote). */
public interface CancelarOperacionUseCase {

    Envio cancelarEnvio(String identificadorNegocio, Long idEnvio, String usuario);

    Pedido cancelarPedido(Long idPedido, String usuario);
}
