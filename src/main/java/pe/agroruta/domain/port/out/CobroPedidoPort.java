package pe.agroruta.domain.port.out;

import java.util.Collection;
import java.util.Map;

import pe.agroruta.domain.model.ResumenCobro;

public interface CobroPedidoPort {

    /** Lo que hay que cobrar y lo cobrado de cada pedido; los pedidos sin liquidación ni pagos no aparecen. */
    Map<Long, ResumenCobro> resumenDe(Collection<Long> idPedidos);
}
