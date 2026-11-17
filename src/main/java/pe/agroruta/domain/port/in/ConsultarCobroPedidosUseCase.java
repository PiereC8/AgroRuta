package pe.agroruta.domain.port.in;

import java.util.Collection;
import java.util.Map;

import pe.agroruta.domain.model.EstadoCobro;

// RFF-07 y cobros: si cada pedido ya se cobró, para verlo en la lista de pedidos.
public interface ConsultarCobroPedidosUseCase {

    Map<Long, EstadoCobro> estados(Collection<Long> idPedidos);
}
