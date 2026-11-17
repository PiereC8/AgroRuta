package pe.agroruta.application.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import pe.agroruta.domain.model.EstadoCobro;
import pe.agroruta.domain.model.ResumenCobro;
import pe.agroruta.domain.port.in.ConsultarCobroPedidosUseCase;
import pe.agroruta.domain.port.out.CobroPedidoPort;

public class ConsultarCobroPedidosService implements ConsultarCobroPedidosUseCase {

    private final CobroPedidoPort cobros;

    public ConsultarCobroPedidosService(CobroPedidoPort cobros) {
        this.cobros = cobros;
    }

    @Override
    public Map<Long, EstadoCobro> estados(Collection<Long> idPedidos) {
        Map<Long, EstadoCobro> estados = new HashMap<>();
        if (idPedidos.isEmpty()) {
            return estados;
        }
        Map<Long, ResumenCobro> resumen = cobros.resumenDe(idPedidos);
        for (Long id : idPedidos) {
            estados.put(id, EstadoCobro.segun(resumen.get(id)));
        }
        return estados;
    }
}
